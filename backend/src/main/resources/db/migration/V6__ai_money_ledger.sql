CREATE TABLE ai_money_days (
 owner_id UUID NOT NULL REFERENCES users(id), budget_day DATE NOT NULL,
 currency VARCHAR(3) NOT NULL CHECK(currency ~ '^[A-Z]{3}$'),
 reserved NUMERIC(30,8) NOT NULL DEFAULT 0 CHECK(reserved>=0),
 consumed NUMERIC(30,8) NOT NULL DEFAULT 0 CHECK(consumed>=0),
 PRIMARY KEY(owner_id,budget_day,currency)
);
CREATE TABLE ai_job_costs (
 job_id UUID PRIMARY KEY, owner_id UUID NOT NULL, budget_day DATE NOT NULL,
 currency VARCHAR(3) NOT NULL, provider_label VARCHAR(120) NOT NULL, model VARCHAR(120) NOT NULL,
 price_version VARCHAR(120) NOT NULL, price_date DATE NOT NULL,
 input_per_million NUMERIC(24,12) NOT NULL CHECK(input_per_million>=0),
 output_per_million NUMERIC(24,12) NOT NULL CHECK(output_per_million>=0),
 reserved NUMERIC(30,8) NOT NULL CHECK(reserved>0), charged NUMERIC(30,8) CHECK(charged>=0),
 source VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN' CHECK(source IN ('UNKNOWN','PRICED_USAGE','NOT_SENT')),
 FOREIGN KEY(owner_id,job_id) REFERENCES ai_generation_jobs(owner_id,id),
 FOREIGN KEY(owner_id,budget_day,currency) REFERENCES ai_money_days(owner_id,budget_day,currency)
);
