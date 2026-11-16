package dev.scarletnexus.transfer;

import java.time.Instant;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface TransferMapper {
  public record Import(
      UUID id,
      String documentJson,
      String sourceHash,
      String state,
      int revision,
      String requestKey,
      String resultJson,
      Instant createdAt,
      Instant confirmedAt) {}

  @Insert(
      "INSERT INTO catalog_imports(id,owner_id,document_json,source_hash) VALUES(#{id},#{owner},CAST(#{document} AS jsonb),#{hash})")
  void create(UUID owner, UUID id, String document, String hash);

  @Select(
      "SELECT id,document_json::text,source_hash,state,revision,request_key,result_json::text,created_at,confirmed_at FROM catalog_imports WHERE owner_id=#{owner} AND id=#{id}")
  Import get(UUID owner, UUID id);

  @Select(
      "SELECT id,document_json::text,source_hash,state,revision,request_key,result_json::text,created_at,confirmed_at FROM catalog_imports WHERE owner_id=#{owner} AND request_key=#{key}")
  Import replay(UUID owner, String key);

  @Update(
      "UPDATE catalog_imports SET state='CONFIRMED',revision=revision+1,request_key=#{key},result_json=CAST(#{result} AS jsonb),confirmed_at=now() WHERE owner_id=#{owner} AND id=#{id}")
  void confirm(UUID owner, UUID id, String key, String result);

  // Table names are exclusively selected from TransferService.SNAPSHOT_TABLES, never request input.
  @Select(
      "SELECT count(*) AS rows,COALESCE(sum(octet_length(to_jsonb(t)::text)),0)::bigint AS bytes FROM ${table} t WHERE owner_id=#{owner}")
  Map<String, Object> size(UUID owner, String table);

  @Select(
      "SELECT (to_jsonb(t)-'owner_id'-'storage_key')::text FROM ${table} t WHERE owner_id=#{owner} ORDER BY to_jsonb(t)::text")
  List<String> rows(UUID owner, String table);
}
