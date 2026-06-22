import { create } from 'zustand';

export interface WorkflowNode {
  id: string;
  type: string;
  label: string;
  data: Record<string, any>;
  position: { x: number; y: number };
}

export interface WorkflowEdge {
  id: string;
  source: string;
  target: string;
  label?: string;
  data?: Record<string, any>;
}

export interface WorkflowGraph {
  nodes: WorkflowNode[];
  edges: WorkflowEdge[];
  metadata?: Record<string, any>;
}

export interface WorkflowVersion {
  id: string;
  workflowDefinitionId: string;
  version: number;
  status: string; // DRAFT, REVIEW, APPROVED, PUBLISHED
  definition: WorkflowGraph;
  createdAt: string;
  createdBy: string;
  updatedAt: string;
  updatedBy: string;
}

export interface WorkflowDefinition {
  id: string;
  name: string;
  key: string;
  description: string;
  createdAt: string;
  updatedAt: string;
  versions: WorkflowVersion[];
  activeVersion?: number;
}

export interface StepRecord {
  stepIndex: number;
  nodeId: string;
  nodeType: string;
  label: string;
  status: string; // ENTERED, EVALUATED, ROUTED, COMPLETED, FAILED, SKIPPED
  expression?: string;
  expressionResult?: any;
  edgeTaken?: string;
  notes?: string;
  enteredAt?: string;
  exitedAt?: string;
  durationMs?: number;
}

export interface ExecutionLog {
  id: string;
  instanceId?: string;
  workflowKey: string;
  versionId: string;
  versionNumber: number;
  contextId: string;
  status: string; // COMPLETED, FAILED, WAITING
  outcomeNodeId?: string;
  outcomeNodeLabel?: string;
  inputContext: Record<string, any>;
  executionTrace: StepRecord[];
  startedAt: string;
  completedAt?: string;
  totalDurationMs: number;
  errorMessage?: string;
}

export interface ContextField {
  id?: string;
  schemaId?: string;
  fieldKey: string;
  displayName: string;
  fieldType: string; // STRING, NUMBER, BOOLEAN, DATE
  required: boolean;
  defaultValue?: string;
  description?: string;
  fieldOrder?: number;
  integrationId?: string;
  responseMapping?: string;
  cacheable?: boolean;
  ttlSeconds?: number;
  cost?: string;
  expression?: string;
}

export interface ContextSchema {
  id: string;
  workflowKey: string;
  name: string;
  description?: string;
  createdAt?: string;
  updatedAt?: string;
  fields: ContextField[];
}

export interface Bucket {
  id: string;
  bucketId: string;
  name: string;
  description?: string;
  category?: string;
  priority: string; // CRITICAL, HIGH, MEDIUM, LOW
  slaHours?: number;
  ownerGroup?: string;
  autoActions?: string;
  active: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface Rule {
  id: string;
  ruleKey: string;
  name: string;
  description?: string;
  expression: string;
  active: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface Integration {
  id?: string;
  integrationKey: string;
  name: string;
  providerType: string; // REST, DB, CONFIG
  endpointUrl?: string;
  method?: string; // GET, POST
  headersJson?: string;
  requestTemplate?: string;
  timeoutMs?: number;
}

export interface EventDefinition {
  id: string;
  eventKey: string;
  name: string;
  description?: string;
  kafkaTopic?: string;
  correlationKeyPath?: string;
  payloadSchema?: string;
  active: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface BucketExecution {
  id: string;
  executionLogId: string;
  instanceId?: string;
  workflowKey: string;
  bucketId: string;
  bucketName: string;
  status: string; // PENDING, IN_REVIEW, RESOLVED
  priority?: string;
  slaHours?: number;
  slaBreached: boolean;
  createdAt: string;
  resolvedAt?: string;
  resolvedBy?: string;
  resolutionNotes?: string;
}

export interface BucketWorkload {
  bucketId: string;
  bucketName: string;
  priority: string;
  slaHours?: number;
  ownerGroup?: string;
  totalRouted: number;
  pending: number;
  inReview: number;
  resolved: number;
  slaBreached: number;
  avgResolutionHours?: number;
}

export interface WorkflowInstance {
  id: string;
  workflowKey: string;
  versionId: string;
  versionNumber: number;
  status: string; // RUNNING, WAITING, COMPLETED, FAILED, TERMINATED
  currentNodeId?: string;
  currentNodeLabel?: string;
  context: Record<string, any>;
  createdAt: string;
  updatedAt: string;
}

interface WorkflowState {
  workflows: WorkflowDefinition[];
  selectedWorkflow: WorkflowDefinition | null;
  selectedVersion: WorkflowVersion | null;
  sidebarOpen: boolean;
  activeRole: string;
  currentView: 'dashboard' | 'manageWorkflows' | 'designer' | 'executor' | 'replay' | 'contextSchema' | 'buckets' | 'rules' | 'ruleHelp' | 'bucketWorkload' | 'integrations' | 'instances' | 'customerForms' | 'executions' | 'events';
  viewHistory: ('dashboard' | 'manageWorkflows' | 'designer' | 'executor' | 'replay' | 'contextSchema' | 'buckets' | 'rules' | 'ruleHelp' | 'bucketWorkload' | 'integrations' | 'instances' | 'customerForms' | 'executions' | 'events')[];
  designerNodes: WorkflowNode[];
  designerEdges: WorkflowEdge[];
  themeMode: 'vi-light'
  | 'vi-dark'
  | 'cosmos-dark'
  | 'slate-dark'
  | 'nord-light'
  | 'emerald-light'
  | 'neural-dark'
  | 'cyberpunk-dark'
  | 'sunset-dark'
  | 'ruby-dark'
  | 'ocean-light'
  | 'lavender-light'
  | 'coffee-dark';
  // Execution state
  executions: ExecutionLog[];
  currentExecution: ExecutionLog | null;
  replayVersion: WorkflowVersion | null;
  // Context Schema, Bucket Registry, & Rule Registry state
  contextSchemas: ContextSchema[];
  buckets: Bucket[];
  rules: Rule[];
  integrations: Integration[];
  instances: WorkflowInstance[];
  events: EventDefinition[];
  // Actions
  setWorkflows: (workflows: WorkflowDefinition[]) => void;
  setSelectedWorkflow: (workflow: WorkflowDefinition | null) => void;
  setSelectedVersion: (version: WorkflowVersion | null) => void;
  toggleSidebar: () => void;
  setActiveRole: (role: string) => void;
  setView: (view: 'dashboard' | 'manageWorkflows' | 'designer' | 'executor' | 'replay' | 'contextSchema' | 'buckets' | 'rules' | 'ruleHelp' | 'bucketWorkload' | 'integrations' | 'instances' | 'customerForms' | 'executions' | 'events') => void;
  goBack: () => void;
  setThemeMode: (mode: 'vi-light'
    | 'vi-dark'
    | 'cosmos-dark'
    | 'slate-dark'
    | 'nord-light'
    | 'emerald-light'
    | 'neural-dark'
    | 'cyberpunk-dark'
    | 'sunset-dark'
    | 'ruby-dark'
    | 'ocean-light'
    | 'lavender-light'
    | 'coffee-dark') => void;
  setDesignerNodes: (nodes: WorkflowNode[]) => void;
  setDesignerEdges: (edges: WorkflowEdge[]) => void;
  setExecutions: (executions: ExecutionLog[]) => void;
  setCurrentExecution: (execution: ExecutionLog | null) => void;
  setReplayVersion: (version: WorkflowVersion | null) => void;
  setContextSchemas: (schemas: ContextSchema[]) => void;
  setBuckets: (buckets: Bucket[]) => void;
  setRules: (rules: Rule[]) => void;
  setIntegrations: (integrations: Integration[]) => void;
  setInstances: (instances: WorkflowInstance[]) => void;
  setEvents: (events: EventDefinition[]) => void;
}

export const useWorkflowStore = create<WorkflowState>((set) => ({
  workflows: [],
  selectedWorkflow: null,
  selectedVersion: null,
  sidebarOpen: true,
  activeRole: 'Author',
  currentView: 'dashboard',
  viewHistory: [],
  designerNodes: [],
  designerEdges: [],
  themeMode: 'vi-light',
  executions: [],
  currentExecution: null,
  replayVersion: null,
  contextSchemas: [],
  buckets: [],
  rules: [],
  integrations: [],
  instances: [],
  events: [],
  setWorkflows: (workflows) => set({ workflows }),
  setSelectedWorkflow: (selectedWorkflow) => set((state) => {
    let selectedVersion = null;
    if (selectedWorkflow?.versions?.length) {
      if (state.selectedVersion && state.selectedWorkflow?.id === selectedWorkflow.id) {
        selectedVersion = selectedWorkflow.versions.find(v => v.id === state.selectedVersion?.id) || null;
      }
      if (!selectedVersion) {
        if (selectedWorkflow.activeVersion) {
          selectedVersion = selectedWorkflow.versions.find(v => v.version === selectedWorkflow.activeVersion) || null;
        }
        if (!selectedVersion) {
          selectedVersion = selectedWorkflow.versions[selectedWorkflow.versions.length - 1];
        }
      }
    }
    return { selectedWorkflow, selectedVersion };
  }),
  setSelectedVersion: (selectedVersion) => set({ selectedVersion }),
  toggleSidebar: () => set((state) => ({ sidebarOpen: !state.sidebarOpen })),
  setActiveRole: (activeRole) => set({ activeRole }),
  setView: (currentView) => set((state) => {
    if (state.currentView === currentView) {
      return {};
    }
    return {
      currentView,
      viewHistory: [...state.viewHistory, state.currentView]
    };
  }),
  goBack: () => set((state) => {
    const history = [...state.viewHistory];
    const prevView = history.pop();
    if (prevView) {
      return { currentView: prevView, viewHistory: history };
    }
    return { currentView: 'dashboard', viewHistory: [] };
  }),
  setThemeMode: (themeMode) => set({ themeMode }),
  setDesignerNodes: (designerNodes) => set({ designerNodes }),
  setDesignerEdges: (designerEdges) => set({ designerEdges }),
  setExecutions: (executions) => set({ executions }),
  setCurrentExecution: (currentExecution) => set({ currentExecution }),
  setReplayVersion: (replayVersion) => set({ replayVersion }),
  setContextSchemas: (contextSchemas) => set({ contextSchemas }),
  setBuckets: (buckets) => set({ buckets }),
  setRules: (rules) => set({ rules }),
  setIntegrations: (integrations) => set({ integrations }),
  setInstances: (instances) => set({ instances }),
  setEvents: (events) => set({ events }),
}));
