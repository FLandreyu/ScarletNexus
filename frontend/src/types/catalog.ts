export interface Skill {
  id: string
  name: string
  description: string
  category: string
  status: 'ACTIVE' | 'ARCHIVED'
}
export interface KnowledgeNode {
  id: string
  title: string
  description: string
  nodeType: 'CATEGORY' | 'CONCEPT' | 'PRACTICE'
  status: 'ACTIVE' | 'ARCHIVED'
}
export interface PlacedNode extends KnowledgeNode {
  parentNodeId: string | null
  displayOrder: number
}
export interface Relation {
  id: string
  sourceNodeId: string
  targetNodeId: string
  relationType: 'PREREQUISITE_OF' | 'RELATED_TO'
}
export interface Graph {
  skill: Skill
  nodes: PlacedNode[]
  relations: Relation[]
}
export interface Page<T> {
  items: T[]
  page: number
  size: number
  total: number
}
