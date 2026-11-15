package dev.scarletnexus.identity;

import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {
  @Select("SELECT id,email,password_hash,status FROM users WHERE email=#{email}")
  UserAccount byEmail(String email);

  @Select("SELECT count(*) FROM users")
  int count();

  @Select("SELECT 1 FROM pg_advisory_xact_lock(710132109)")
  int lockBootstrap();

  @Insert("INSERT INTO users(id,email,password_hash) VALUES(#{id},#{email},#{hash})")
  void insert(UUID id, String email, String hash);

  @Insert(
      "INSERT INTO audit_events(id,user_id,entity_type,entity_id,action,request_id) VALUES(#{eventId},#{userId},'USER',#{userId},'BOOTSTRAPPED','bootstrap')")
  void auditBootstrap(UUID eventId, UUID userId);
}
