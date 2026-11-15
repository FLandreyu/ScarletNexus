CREATE TABLE ai_generation_inputs (
 job_id UUID PRIMARY KEY, owner_id UUID NOT NULL,
 input_text TEXT NOT NULL CHECK(length(input_text)<=50000),
 root_job_id UUID NOT NULL, parent_job_id UUID UNIQUE, depth INT NOT NULL CHECK(depth BETWEEN 0 AND 2),
 FOREIGN KEY(owner_id,job_id) REFERENCES ai_generation_jobs(owner_id,id),
 FOREIGN KEY(owner_id,root_job_id) REFERENCES ai_generation_jobs(owner_id,id),
 FOREIGN KEY(owner_id,parent_job_id) REFERENCES ai_generation_jobs(owner_id,id),
 CHECK((depth=0 AND parent_job_id IS NULL AND root_job_id=job_id) OR (depth>0 AND parent_job_id IS NOT NULL))
);
CREATE INDEX ai_generation_inputs_root_idx ON ai_generation_inputs(owner_id,root_job_id);
