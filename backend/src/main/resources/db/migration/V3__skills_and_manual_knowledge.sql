CREATE TABLE skills (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(120) NOT NULL CHECK (length(btrim(name)) > 0),
    description TEXT NOT NULL DEFAULT '',
    category VARCHAR(80) NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ARCHIVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(owner_id,id)
);
CREATE INDEX skills_owner_status_idx ON skills(owner_id,status,updated_at DESC);
CREATE TABLE knowledge_nodes (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id),
    title VARCHAR(160) NOT NULL CHECK (length(btrim(title)) > 0),
    node_type VARCHAR(16) NOT NULL CHECK (node_type IN ('CATEGORY','CONCEPT','PRACTICE')),
    description TEXT NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ARCHIVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(owner_id,id)
);
CREATE INDEX knowledge_nodes_owner_idx ON knowledge_nodes(owner_id,status,updated_at DESC);
CREATE TABLE skill_nodes (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    skill_id UUID NOT NULL,
    node_id UUID NOT NULL,
    parent_node_id UUID,
    display_order INT NOT NULL DEFAULT 0 CHECK (display_order >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(owner_id,skill_id,node_id),
    FOREIGN KEY(owner_id,skill_id) REFERENCES skills(owner_id,id),
    FOREIGN KEY(owner_id,node_id) REFERENCES knowledge_nodes(owner_id,id),
    FOREIGN KEY(owner_id,skill_id,parent_node_id) REFERENCES skill_nodes(owner_id,skill_id,node_id),
    CHECK(parent_node_id IS NULL OR parent_node_id <> node_id)
);
CREATE INDEX skill_nodes_order_idx ON skill_nodes(skill_id,display_order);
CREATE TABLE knowledge_relations (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    source_node_id UUID NOT NULL,
    target_node_id UUID NOT NULL,
    relation_type VARCHAR(24) NOT NULL CHECK (relation_type IN ('PREREQUISITE_OF','RELATED_TO')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY(owner_id,source_node_id) REFERENCES knowledge_nodes(owner_id,id),
    FOREIGN KEY(owner_id,target_node_id) REFERENCES knowledge_nodes(owner_id,id),
    UNIQUE(owner_id,source_node_id,target_node_id,relation_type),
    CHECK(source_node_id <> target_node_id)
);
CREATE INDEX knowledge_relations_target_idx ON knowledge_relations(owner_id,target_node_id,relation_type);
