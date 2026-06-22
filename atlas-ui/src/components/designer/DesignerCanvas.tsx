import React, { useEffect, useState, useCallback } from 'react';
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  addEdge,
  useNodesState,
  useEdgesState,
  ReactFlowProvider,
  useReactFlow
} from '@xyflow/react';
import type { Connection, Node } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { Box, Typography, Button, Chip, AppBar, Toolbar, IconButton, CircularProgress, useTheme } from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import SaveIcon from '@mui/icons-material/Save';
import DeleteIcon from '@mui/icons-material/Delete';
import VisibilityIcon from '@mui/icons-material/Visibility';
import { useWorkflowStore } from '../../store/workflowStore';
import type { StepRecord } from '../../store/workflowStore';
import { NodeCatalog } from './NodeCatalog';
import { nodeTypes } from './CustomNode';
import { NodePropertiesDrawer } from './NodePropertiesDrawer';
import { EdgePropertiesDrawer } from './EdgePropertiesDrawer';
import { TraceTimeline } from '../TraceTimeline';

interface DesignerCanvasInnerProps {
  onRefreshWorkflows: () => Promise<void>;
  onShowNotification: (message: string, severity: 'success' | 'error') => void;
  // Trace mode props (Sprint 4)
  traceMode?: boolean;
  executionTrace?: StepRecord[];
  activeTraceStep?: number | null;
  setActiveTraceStep?: (step: number | null) => void;
  hideHeader?: boolean;
  hideTimeline?: boolean;
  runtimeGraph?: {
    nodes: any[];
    edges: any[];
  } | null;
}

// Build map of nodeId → step for overlay rendering
const buildTraceMap = (trace: StepRecord[]): Map<string, StepRecord> => {
  const map = new Map<string, StepRecord>();
  trace.forEach(s => map.set(s.nodeId, s));
  return map;
};

const STEP_STATUS_GLOW: Record<string, string> = {
  COMPLETED: '16, 185, 129',  // green
  EVALUATED: '245, 158, 11',  // yellow
  ROUTED: '20, 184, 166',    // teal
  ENTERED: '99, 102, 241',    // indigo
  FAILED: '239, 68, 68',      // red
  SKIPPED: '107, 114, 128',   // gray
};

const EMPTY_TRACE: StepRecord[] = [];

const DesignerCanvasInner: React.FC<DesignerCanvasInnerProps> = ({
  onRefreshWorkflows,
  onShowNotification,
  traceMode = false,
  executionTrace = EMPTY_TRACE,
  activeTraceStep: propActiveTraceStep,
  setActiveTraceStep: propSetActiveTraceStep,
  hideHeader = false,
  hideTimeline = false,
  runtimeGraph = null,
}) => {
  const { selectedWorkflow, selectedVersion, goBack } = useWorkflowStore();
  const { screenToFlowPosition, getNodes, getEdges } = useReactFlow();
  const theme = useTheme();

  const [nodes, setNodes, onNodesChange] = useNodesState<any>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<any>([]);
  const [isSaving, setIsSaving] = useState(false);
  const [selectedNode, setSelectedNode] = useState<any | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [selectedEdge, setSelectedEdge] = useState<any | null>(null);
  const [edgeDrawerOpen, setEdgeDrawerOpen] = useState(false);
  
  const [localActiveTraceStep, setLocalActiveTraceStep] = useState<number | null>(null);
  const activeTraceStep = propActiveTraceStep !== undefined ? propActiveTraceStep : localActiveTraceStep;
  const setActiveTraceStep = propSetActiveTraceStep !== undefined ? propSetActiveTraceStep : setLocalActiveTraceStep;

  const traceMap = React.useMemo(() => buildTraceMap(executionTrace), [executionTrace]);

  const isReadOnly = traceMode || (selectedVersion ? selectedVersion.status.toUpperCase() !== 'DRAFT' : true);

  // Load initial nodes/edges and apply trace overlays
  useEffect(() => {
    const hasRuntimeGraph = runtimeGraph && Array.isArray(runtimeGraph.nodes) && Array.isArray(runtimeGraph.edges) && runtimeGraph.nodes.length > 0;
    const baseNodes = hasRuntimeGraph ? runtimeGraph.nodes : (selectedVersion?.definition?.nodes || []);
    const baseEdges = hasRuntimeGraph ? runtimeGraph.edges : (selectedVersion?.definition?.edges || []);

    if (hasRuntimeGraph || selectedVersion?.definition) {
      const rawNodes = baseNodes;
      const rawEdges = baseEdges;

      if (traceMode && executionTrace.length > 0) {
        // Slice execution trace up to activeTraceStep
        const traceSlice = activeTraceStep !== null
          ? executionTrace.slice(0, activeTraceStep + 1)
          : executionTrace;

        const sliceTraceMap = buildTraceMap(traceSlice);
        const activeNodeId = (activeTraceStep !== null && executionTrace[activeTraceStep])
          ? executionTrace[activeTraceStep].nodeId
          : null;

        // Apply glow styling to traversed nodes
        const styledNodes = rawNodes.map((n: any) => {
          const step = sliceTraceMap.get(n.id);
          if (!step) return n;
          const glowRgb = STEP_STATUS_GLOW[step.status] || '99,102,241';
          return {
            ...n,
            style: {
              ...n.style,
              filter: `drop-shadow(0 0 10px rgba(${glowRgb}, 0.8))`,
              opacity: 1,
            }
          };
        });

        // Dim unvisited nodes and highlight active node
        const visitedIds = new Set(traceSlice.map(s => s.nodeId));
        const finalNodes = styledNodes.map((n: any) => {
          const isVisited = visitedIds.has(n.id);
          const isActive = n.id === activeNodeId;
          
          let filter = n.style?.filter || 'none';
          if (isActive) {
            filter = `drop-shadow(0 0 20px rgba(255, 255, 255, 1.0)) drop-shadow(0 0 10px rgba(99, 102, 241, 0.8))`;
          } else if (!isVisited) {
            filter = 'none';
          }

          return {
            ...n,
            style: {
              ...n.style,
              opacity: isVisited ? 1 : 0.25,
              filter,
              transition: 'filter 0.3s, opacity 0.3s',
            }
          };
        });

        // Style traversed edges
        const visitedEdges = new Set(traceSlice.filter(s => s.edgeTaken).map(s => s.edgeTaken!));
        const styledEdges = rawEdges.map((e: any) => ({
          ...e,
          style: visitedEdges.has(e.id)
            ? { stroke: '#10b981', strokeWidth: 3 }
            : { stroke: 'rgba(255,255,255,0.1)', strokeWidth: 1 },
          animated: visitedEdges.has(e.id),
        }));

        setNodes(finalNodes);
        setEdges(styledEdges);
      } else {
        setNodes(rawNodes);
        setEdges(rawEdges);
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedVersion, traceMode, executionTrace, activeTraceStep, runtimeGraph]);

  // Auto-select active trace step node to show properties in drawer
  useEffect(() => {
    if (traceMode && activeTraceStep !== null && executionTrace[activeTraceStep]) {
      const step = executionTrace[activeTraceStep];
      const hasRuntimeGraph = runtimeGraph && Array.isArray(runtimeGraph.nodes) && Array.isArray(runtimeGraph.edges) && runtimeGraph.nodes.length > 0;
      const rawNodes = hasRuntimeGraph ? runtimeGraph.nodes : (selectedVersion?.definition?.nodes || []);
      const node = rawNodes.find((n: any) => n.id === step.nodeId);
      if (node) {
        setSelectedNode(node);
        setDrawerOpen(true);
      }
    }
  }, [activeTraceStep, traceMode, selectedVersion, runtimeGraph]);

  const onConnect = (params: Connection) => {
    if (isReadOnly) return;
    const newEdge = {
      ...params,
      id: `e-${Date.now()}`,
      style: { stroke: '#6366f1', strokeWidth: 2 },
      animated: true
    };
    setEdges((eds) => addEdge(newEdge, eds));
  };

  const onDragOver = (event: React.DragEvent) => {
    event.preventDefault();
    event.dataTransfer.dropEffect = 'move';
  };

  const onDrop = (event: React.DragEvent) => {
    event.preventDefault();
    if (isReadOnly) return;
    const type = event.dataTransfer.getData('application/reactflow');
    if (!type) return;
    const position = screenToFlowPosition({ x: event.clientX, y: event.clientY });
    const newNode = {
      id: `${type.toLowerCase()}-${Date.now()}`,
      type,
      position,
      data: {
        label: `${type.charAt(0) + type.slice(1).toLowerCase()} Node`,
        bucketId: type === 'BUCKET' ? '' : undefined,
        ruleId: type === 'RULE' ? '' : undefined,
      },
    };
    setNodes((nds) => nds.concat(newNode));
  };

  const onDeleteSelected = () => {
    if (isReadOnly) return;
    setNodes((nds) => nds.filter((n) => !n.selected));
    setEdges((eds) => eds.filter((e) => !e.selected));
  };

  const onNodeClick = useCallback((_event: React.MouseEvent, node: Node) => {
    setSelectedNode(node);
    setDrawerOpen(true);
    setSelectedEdge(null);
    setEdgeDrawerOpen(false);
  }, []);

  const onEdgeClick = useCallback((_event: React.MouseEvent, edge: any) => {
    setSelectedEdge(edge);
    setEdgeDrawerOpen(true);
    setSelectedNode(null);
    setDrawerOpen(false);
  }, []);

  const onPaneClick = useCallback(() => {
    setSelectedNode(null);
    setDrawerOpen(false);
    setSelectedEdge(null);
    setEdgeDrawerOpen(false);
  }, []);

  const saveWorkflow = async (nodesToSave: any[], edgesToSave: any[]) => {
    if (!selectedVersion) return;
    setIsSaving(true);
    try {
      const currentNodes = nodesToSave.map(n => ({
        id: n.id, type: n.type || 'RULE',
        label: n.data?.label || '',
        data: n.data || {},
        position: n.position
      }));
      const currentEdges = edgesToSave.map(e => ({
        id: e.id, source: e.source, target: e.target,
        label: e.label || '', data: e.data || {}
      }));
      const response = await fetch(`/api/workflows/versions/${selectedVersion.id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ nodes: currentNodes, edges: currentEdges, metadata: selectedVersion.definition?.metadata || {} })
      });
      if (!response.ok) {
        const err = await response.json();
        throw new Error(err.error || 'Failed to save');
      }
      onShowNotification('Workflow design saved!', 'success');
      await onRefreshWorkflows();
    } catch (error: any) {
      onShowNotification(error.message || 'Save failed', 'error');
    } finally {
      setIsSaving(false);
    }
  };

  const handleSave = async () => {
    await saveWorkflow(getNodes(), getEdges());
  };

  const handleSaveNode = async (nodeId: string, updates: { label: string; data: Record<string, any> }) => {
    // Update node in local state
    const updatedNodes = nodes.map((n: any) =>
      n.id === nodeId
        ? { ...n, data: { ...n.data, ...updates.data, label: updates.label }, label: updates.label }
        : n
    );
    setNodes(updatedNodes);
    
    // Update selectedNode state to prevent stale data in drawer
    const updatedNode = updatedNodes.find((n: any) => n.id === nodeId);
    if (updatedNode) {
      setSelectedNode(updatedNode);
    }
    
    // Persist updated list immediately, bypassing stale state in React Flow store
    await saveWorkflow(updatedNodes, getEdges());
  };

  const handleDeleteNode = async (nodeId: string) => {
    if (isReadOnly) return;
    setNodes((nds) => nds.filter((n) => n.id !== nodeId));
    setEdges((eds) => eds.filter((e) => e.source !== nodeId && e.target !== nodeId));
    setSelectedNode(null);
    setDrawerOpen(false);
    setTimeout(async () => {
      await handleSave();
    }, 50);
  };

  const handleSaveEdge = async (edgeId: string, updates: { label: string; condition: string }) => {
    const updatedEdges = edges.map((e: any) =>
      e.id === edgeId
        ? {
            ...e,
            label: updates.label,
            data: { ...e.data, condition: updates.condition }
          }
        : e
    );
    setEdges(updatedEdges);
    
    // Update selectedEdge state to prevent stale data in drawer
    const updatedEdge = updatedEdges.find((e: any) => e.id === edgeId);
    if (updatedEdge) {
      setSelectedEdge(updatedEdge);
    }
    
    await saveWorkflow(nodes, updatedEdges);
  };

  const handleDeleteEdge = async (edgeId: string) => {
    if (isReadOnly) return;
    const updatedEdges = edges.filter((e: any) => e.id !== edgeId);
    setEdges(updatedEdges);
    setSelectedEdge(null);
    setEdgeDrawerOpen(false);
    await saveWorkflow(nodes, updatedEdges);
  };

  const getStatusColor = (status: string) => {
    switch (status?.toUpperCase()) {
      case 'DRAFT': return 'default';
      case 'REVIEW': return 'warning';
      case 'APPROVED': return 'info';
      case 'PUBLISHED': return 'success';
      default: return 'default';
    }
  };

  const activeNode = selectedNode ? nodes.find((n: any) => n.id === selectedNode.id) : null;
  const activeEdge = selectedEdge ? edges.find((e: any) => e.id === selectedEdge.id) : null;

  const traceStep = activeNode && traceMode
    ? traceMap.get(activeNode.id) || null
    : null;

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: hideHeader ? '100%' : '100vh', width: hideHeader ? '100%' : '100vw', overflow: 'hidden' }}>
      {/* Header */}
      {!hideHeader && (
        <AppBar position="static" sx={{ bgcolor: 'background.paper', color: 'text.primary', borderBottom: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
          <Toolbar variant="dense">
            <IconButton edge="start" color="inherit" onClick={() => goBack()} sx={{ mr: 2 }}>
              <ArrowBackIcon />
            </IconButton>
            <Box sx={{ flexGrow: 1 }}>
              <Typography variant="subtitle1" color="text.primary" sx={{ display: 'flex', alignItems: 'center', gap: 1.5, fontWeight: 700 }}>
                {selectedWorkflow?.name}
                <Chip label={`v${selectedVersion?.version}`} size="small" variant="outlined" sx={{ height: 20, fontSize: '10px', fontWeight: 700 }} />
                <Chip label={selectedVersion?.status} color={getStatusColor(selectedVersion?.status || 'DRAFT')} size="small" sx={{ height: 20, fontSize: '9px', fontWeight: 800 }} />
                {traceMode && (
                  <Chip label="TRACE REPLAY" icon={<VisibilityIcon sx={{ fontSize: 10 }} />} size="small"
                    sx={{ height: 20, fontSize: '9px', fontWeight: 800, bgcolor: 'rgba(16,185,129,0.15)', color: '#10b981', border: '1px solid rgba(16,185,129,0.3)' }} />
                )}
              </Typography>
              <Typography variant="caption" color="text.secondary">ID: {selectedWorkflow?.key}</Typography>
            </Box>
            <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
              {!isReadOnly && (
                <>
                  <Button variant="outlined" color="error" size="small" startIcon={<DeleteIcon />}
                    onClick={onDeleteSelected}
                    sx={{ borderColor: 'rgba(244,67,54,0.3)' }}>
                    Delete Selected
                  </Button>
                  <Button variant="contained" color="primary" size="small"
                    startIcon={isSaving ? <CircularProgress size={14} color="inherit" /> : <SaveIcon />}
                    onClick={handleSave} disabled={isSaving}
                    sx={{ background: 'linear-gradient(135deg, #6366f1, #4f46e5)', boxShadow: '0 4px 10px rgba(99,102,241,0.3)' }}>
                    Save Design
                  </Button>
                </>
              )}
              {isReadOnly && !traceMode && (
                <Typography variant="caption" color="text.secondary" sx={{ fontStyle: 'italic', pr: 2 }}>
                  Read-Only Preview
                </Typography>
              )}
              {traceMode && (
                <Typography variant="caption" color="text.secondary" sx={{ fontStyle: 'italic', pr: 2, color: '#10b981' }}>
                  {executionTrace.length} steps traced
                </Typography>
              )}
            </Box>
          </Toolbar>
        </AppBar>
      )}

      {/* Workspace */}
      <Box sx={{ display: 'flex', flexGrow: 1, overflow: 'hidden' }}>
        {!isReadOnly && <NodeCatalog />}

        <Box sx={{ flexGrow: 1, height: '100%', bgcolor: 'background.default', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
          {/* Canvas */}
          <Box sx={{ flexGrow: 1, overflow: 'hidden' }} onDragOver={onDragOver} onDrop={onDrop}>
            <ReactFlow
              nodes={nodes}
              edges={edges}
              onNodesChange={onNodesChange}
              onEdgesChange={onEdgesChange}
              onConnect={onConnect}
              onNodeClick={onNodeClick}
              onEdgeClick={onEdgeClick}
              onPaneClick={onPaneClick}
              nodeTypes={nodeTypes}
              nodesDraggable={!isReadOnly}
              nodesConnectable={!isReadOnly}
              elementsSelectable={!isReadOnly}
              fitView
            >
              <Background color={theme.palette.mode === 'dark' ? 'rgba(255, 255, 255, 0.06)' : 'rgba(0, 0, 0, 0.06)'} gap={16} size={1} />
              <Controls />
              <MiniMap
                style={{
                  background: theme.palette.background.paper,
                  border: `1px solid ${theme.palette.divider}`,
                  borderRadius: '8px'
                }}
                maskColor={theme.palette.mode === 'dark' ? 'rgba(0, 0, 0, 0.6)' : 'rgba(255, 255, 255, 0.6)'}
                nodeColor={() => theme.palette.primary.main}
              />
            </ReactFlow>
          </Box>

          {/* Trace Timeline (Sprint 4) */}
          {traceMode && executionTrace.length > 0 && !hideTimeline && (
            <TraceTimeline
              trace={executionTrace}
              activeStep={activeTraceStep}
              onStepClick={(i) => setActiveTraceStep(activeTraceStep === i ? null : i)}
            />
          )}
        </Box>

        {/* Node Properties Drawer */}
        <NodePropertiesDrawer
          open={drawerOpen && !!activeNode}
          node={activeNode ? {
            id: activeNode.id,
            type: activeNode.type,
            label: activeNode.data?.label,
            data: activeNode.data,
          } : null}
          isReadOnly={isReadOnly}
          traceStep={traceStep}
          onClose={() => setDrawerOpen(false)}
          onSaveNode={!isReadOnly ? handleSaveNode : undefined}
          onDeleteNode={!isReadOnly ? handleDeleteNode : undefined}
        />

        {/* Edge Properties Drawer */}
        <EdgePropertiesDrawer
          open={edgeDrawerOpen && !!activeEdge}
          edge={activeEdge ? {
            id: activeEdge.id,
            label: activeEdge.label,
            data: activeEdge.data,
          } : null}
          isReadOnly={isReadOnly}
          onClose={() => setEdgeDrawerOpen(false)}
          onSaveEdge={!isReadOnly ? handleSaveEdge : undefined}
          onDeleteEdge={!isReadOnly ? handleDeleteEdge : undefined}
        />
      </Box>
    </Box>
  );
};

export const DesignerCanvas: React.FC<DesignerCanvasInnerProps> = (props) => {
  return (
    <ReactFlowProvider>
      <DesignerCanvasInner {...props} />
    </ReactFlowProvider>
  );
};
