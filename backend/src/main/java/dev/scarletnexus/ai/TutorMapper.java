package dev.scarletnexus.ai;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface TutorMapper {
  record Conversation(UUID id, UUID taskId, UUID nodeId, UUID questionId, String title) {}

  record Turn(
      UUID id,
      UUID conversationId,
      UUID attemptId,
      String mode,
      String userText,
      String assistantText,
      String state,
      String errorCode,
      String model,
      String currency,
      BigDecimal reservedMoney,
      BigDecimal chargedMoney,
      String usageSource,
      Long inputTokens,
      Long outputTokens,
      Instant createdAt) {}

  record Run(
      UUID id,
      String inputHash,
      String contextJson,
      LocalDate budgetDay,
      long reservedTokens,
      String currency,
      String providerLabel,
      String model,
      String priceVersion,
      LocalDate priceDate,
      BigDecimal inputRate,
      BigDecimal outputRate,
      BigDecimal reservedMoney) {}

  @Select(
      "SELECT id,task_id,node_id,question_id,title FROM ai_conversations WHERE owner_id=#{owner} AND id=#{id}")
  Conversation conversation(UUID owner, UUID id);

  @Select(
      "SELECT id,task_id,node_id,question_id,title FROM ai_conversations WHERE owner_id=#{owner} AND task_id=#{task} ORDER BY created_at DESC,id LIMIT #{size} OFFSET #{offset}")
  List<Conversation> conversations(UUID owner, UUID task, int size, int offset);

  @Select("SELECT count(*) FROM ai_conversations WHERE owner_id=#{owner} AND task_id=#{task}")
  long conversationCount(UUID owner, UUID task);

  @Insert(
      "INSERT INTO ai_conversations(id,owner_id,task_id,node_id,question_id,title) VALUES(#{id},#{owner},#{task},#{node},#{question},#{title})")
  void insertConversation(UUID owner, UUID id, UUID task, UUID node, UUID question, String title);

  @Select(
      "SELECT id,conversation_id,attempt_id,mode,user_text,assistant_text,state,error_code,model,currency,reserved_money,charged_money,usage_source,input_tokens,output_tokens,created_at FROM tutor_turns WHERE owner_id=#{owner} AND id=#{id}")
  Turn turn(UUID owner, UUID id);

  @Select(
      "SELECT id,input_hash,context_json::text,budget_day,reserved_tokens,currency,provider_label,model,price_version,price_date,input_rate,output_rate,reserved_money FROM tutor_turns WHERE owner_id=#{owner} AND id=#{id}")
  Run run(UUID owner, UUID id);

  @Select(
      "SELECT id,input_hash,context_json::text,budget_day,reserved_tokens,currency,provider_label,model,price_version,price_date,input_rate,output_rate,reserved_money FROM tutor_turns WHERE owner_id=#{owner} AND request_key=#{key}")
  Run key(UUID owner, String key);

  @Select(
      "SELECT id,conversation_id,attempt_id,mode,user_text,assistant_text,state,error_code,model,currency,reserved_money,charged_money,usage_source,input_tokens,output_tokens,created_at FROM tutor_turns WHERE owner_id=#{owner} AND conversation_id=#{conversation} ORDER BY created_at DESC,id LIMIT #{size} OFFSET #{offset}")
  List<Turn> turns(UUID owner, UUID conversation, int size, int offset);

  @Select(
      "SELECT count(*) FROM tutor_turns WHERE owner_id=#{owner} AND conversation_id=#{conversation}")
  long turnCount(UUID owner, UUID conversation);

  @Select("SELECT EXISTS(SELECT 1 FROM tutor_turns WHERE owner_id=#{owner} AND state='RUNNING')")
  boolean running(UUID owner);

  @Insert(
      "INSERT INTO tutor_turns(id,owner_id,conversation_id,attempt_id,request_key,input_hash,user_text,mode,context_json,state,reserved_tokens,budget_day,currency,provider_label,model,price_version,price_date,input_rate,output_rate,reserved_money) VALUES(#{id},#{owner},#{conversation},#{attempt},#{key},#{hash},#{text},#{mode},CAST(#{context} AS JSONB),'RUNNING',#{tokens},#{day},#{p.currency},#{p.provider},#{p.model},#{p.version},#{p.date},#{p.input},#{p.output},#{p.reserve})")
  void insert(
      UUID owner,
      UUID id,
      UUID conversation,
      UUID attempt,
      String key,
      String hash,
      String text,
      String mode,
      String context,
      long tokens,
      LocalDate day,
      MoneyLedger.Price p);

  @Update(
      "UPDATE tutor_turns SET state=#{state},assistant_text=#{text},error_code=#{error},input_tokens=#{input},output_tokens=#{output},charged_money=#{money},usage_source=#{source},finished_at=now() WHERE owner_id=#{owner} AND id=#{id} AND state='RUNNING'")
  void finish(
      UUID owner,
      UUID id,
      String state,
      String text,
      String error,
      Long input,
      Long output,
      BigDecimal money,
      String source);
}
