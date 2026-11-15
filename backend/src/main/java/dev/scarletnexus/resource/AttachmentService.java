package dev.scarletnexus.resource;

import static dev.scarletnexus.common.DomainFailure.require;

import dev.scarletnexus.assessment.*;
import dev.scarletnexus.assessment.PracticeModels.*;
import dev.scarletnexus.common.*;
import dev.scarletnexus.identity.CurrentUser;
import dev.scarletnexus.learning.*;
import dev.scarletnexus.learning.LearningModels.Page;
import dev.scarletnexus.skill.CatalogMapper;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;
import org.apache.ibatis.annotations.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class AttachmentService {
  @Mapper
  public interface Quota {
    @Select("SELECT COALESCE(sum(size_bytes),0) FROM attachments WHERE owner_id=#{owner}")
    long bytes(UUID owner);
  }

  private final PracticeMapper db;
  private final LearningService learning;
  private final CatalogMapper catalog;
  private final AuditLog audit;
  private final Quota quota;
  private final Path directory;
  private final long maxBytes;

  public AttachmentService(
      PracticeMapper db,
      LearningService learning,
      CatalogMapper catalog,
      AuditLog audit,
      Quota quota,
      Environment env)
      throws IOException {
    this.db = db;
    this.learning = learning;
    this.catalog = catalog;
    this.audit = audit;
    this.quota = quota;
    directory =
        Path.of(env.getProperty("app.attachments.directory", ".runtime/attachments"))
            .toAbsolutePath()
            .normalize();
    Files.createDirectories(directory);
    maxBytes = env.getProperty("app.attachments.max-owner-bytes", Long.class, 268435456L);
    if (maxBytes < 5242880 || maxBytes > 10737418240L)
      throw new IllegalArgumentException("Invalid attachment quota");
  }

  public Page<Attachment> list(UUID task, int p, int s) {
    learning.task(task);
    require(p >= 0 && p <= 100000 && s > 0 && s <= 100, "INVALID_PAGE", "分页参数无效");
    return new Page<>(
        db.attachments(CurrentUser.id(), task, s, p * s),
        db.attachmentCount(CurrentUser.id(), task),
        p,
        s);
  }

  public Attachment upload(UUID task, MultipartFile file) throws IOException {
    UUID owner = CurrentUser.id();
    learning.task(task);
    require(file.getSize() > 0 && file.getSize() <= 5242880, "IMAGE_SIZE", "图片大小须在1字节至5MiB之间");
    byte[] input = file.getBytes();
    require(input.length <= 5242880, "IMAGE_SIZE", "图片过大");
    boolean png =
        input.length >= 8
            && Arrays.equals(
                Arrays.copyOf(input, 8), new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});
    boolean jpeg =
        input.length >= 3
            && (input[0] & 255) == 255
            && (input[1] & 255) == 216
            && (input[2] & 255) == 255;
    require(
        (png && "image/png".equals(file.getContentType()))
            || (jpeg && "image/jpeg".equals(file.getContentType())),
        "IMAGE_TYPE",
        "仅接受真实PNG或JPEG图片");
    int width, height;
    byte[] normalized;
    try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
      var readers = ImageIO.getImageReaders(stream);
      require(readers.hasNext(), "IMAGE_INVALID", "无法解析图片");
      var reader = readers.next();
      try {
        reader.setInput(stream, true, true);
        width = reader.getWidth(0);
        height = reader.getHeight(0);
        require(
            width > 0
                && height > 0
                && width <= 8000
                && height <= 8000
                && (long) width * height <= 16000000,
            "IMAGE_PIXELS",
            "图片不能超过8000边长或1600万像素");
        var decoded = reader.read(0);
        var clean = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var graphics = clean.createGraphics();
        try {
          graphics.drawImage(decoded, 0, 0, null);
        } finally {
          graphics.dispose();
        }
        var out = new ByteArrayOutputStream();
        if (!ImageIO.write(clean, "png", out)) throw new IOException("PNG encoder unavailable");
        normalized = out.toByteArray();
      } finally {
        reader.dispose();
      }
    } catch (DomainFailure e) {
      throw e;
    } catch (IOException | RuntimeException e) {
      throw new DomainFailure(
          org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
          "IMAGE_INVALID",
          "图片内容损坏或不能安全解码");
    }
    require(normalized.length <= 20971520, "IMAGE_NORMALIZED_SIZE", "处理后的图片过大");
    if (catalog.lockOwner(owner) == null) throw DomainFailure.missing();
    require(
        quota.bytes(owner) + normalized.length <= maxBytes,
        "ATTACHMENT_QUOTA",
        "图片总空间配额不足，请联系管理员整理存储");
    UUID id = UUID.randomUUID();
    String key = id + ".png", checksum = checksum(normalized);
    Path target = directory.resolve(key),
        temporary = Files.createTempFile(directory, "quarantine-", ".tmp");
    try {
      Files.write(temporary, normalized);
      db.attachmentInsert(owner, id, task, key, checksum, normalized.length, width, height);
      Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
      db.attachmentState(owner, id, "READY");
      var view =
          new Attachment(
              id, task, normalized.length, width, height, "READY", java.time.Instant.now());
      audit.record(
          "attachment",
          id,
          "SANITIZED",
          null,
          Map.of("taskId", task, "width", width, "height", height, "checksum", checksum));
      return view;
    } catch (Exception e) {
      Files.deleteIfExists(target);
      throw e;
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  static String checksum(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception e) {
      throw new IllegalStateException("Checksum unavailable");
    }
  }

  private StoredAttachment get(UUID id) {
    var file = db.storedAttachment(CurrentUser.id(), id);
    if (file == null) throw DomainFailure.missing();
    return file;
  }

  public byte[] content(UUID id) throws IOException {
    var file = get(id);
    if (!file.status().equals("READY")) throw DomainFailure.missing();
    if (!file.storageKey().matches("[0-9a-f-]{36}\\.png"))
      throw new IllegalStateException("Invalid storage key");
    Path path = directory.resolve(file.storageKey()).normalize();
    if (!path.startsWith(directory) || Files.isSymbolicLink(path))
      throw new IllegalStateException("Invalid storage path");
    if (!Files.isRegularFile(path) || Files.size(path) != file.sizeBytes())
      throw new IOException("Stored image unavailable");
    byte[] data = Files.readAllBytes(path);
    if (!checksum(data).equals(file.checksum()))
      throw new IOException("Stored image checksum mismatch");
    return data;
  }

  public void state(UUID id, String status) {
    UUID owner = CurrentUser.id();
    catalog.lockOwner(owner);
    var before = get(id);
    require(!before.status().equals("QUARANTINED"), "ATTACHMENT_UNAVAILABLE", "图片尚未通过处理");
    db.attachmentState(owner, id, status);
    audit.record(
        "attachment", id, "STATE", Map.of("status", before.status()), Map.of("status", status));
  }
}
