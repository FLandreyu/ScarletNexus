package dev.scarletnexus.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.scarletnexus.identity.CurrentUser;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class AuditLog {
  @Mapper
  public interface Events {
    @Insert(
        "INSERT INTO audit_events(id,user_id,entity_type,entity_id,action,before_json,after_json,request_id) VALUES(#{id},#{user},#{type},#{entity},#{action},CAST(#{before} AS JSONB),CAST(#{after} AS JSONB),#{request})")
    void append(
        UUID id,
        UUID user,
        String type,
        UUID entity,
        String action,
        String before,
        String after,
        String request);
  }

  private final Events events;
  private final ObjectMapper json;

  public AuditLog(Events events, ObjectMapper json) {
    this.events = events;
    this.json = json;
  }

  public void record(String type, UUID id, String action, Object before, Object after) {
    try {
      events.append(
          UUID.randomUUID(),
          CurrentUser.id(),
          type,
          id,
          action,
          before == null ? null : json.writeValueAsString(before),
          after == null ? null : json.writeValueAsString(after),
          MDC.get("requestId") == null ? "internal" : MDC.get("requestId"));
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("Cannot serialize audit event");
    }
  }
}
