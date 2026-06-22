import React, { useEffect, useState } from 'react';
import {
  Box, Container, Grid, Paper, Typography, Button, Card, CardContent,
  Chip, IconButton, InputBase, CircularProgress, Divider,
  Dialog, DialogTitle, DialogContent, DialogActions, TextField, Collapse,
  Tooltip, FormControl, Select, MenuItem
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import SearchIcon from '@mui/icons-material/Search';
import RefreshIcon from '@mui/icons-material/Refresh';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import PauseIcon from '@mui/icons-material/Pause';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import CancelIcon from '@mui/icons-material/Cancel';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import KeyboardArrowUpIcon from '@mui/icons-material/KeyboardArrowUp';
import VisibilityIcon from '@mui/icons-material/Visibility';
import { useWorkflowStore } from '../store/workflowStore';
import type { WorkflowInstance, ExecutionLog } from '../store/workflowStore';

interface WorkflowInstancesPageProps {
  onShowNotification: (message: string, severity: 'success' | 'error') => void;
}

export const WorkflowInstancesPage: React.FC<WorkflowInstancesPageProps> = ({ onShowNotification }) => {
  const { setView, setCurrentExecution, setReplayVersion, workflows, goBack } = useWorkflowStore();
  const [instances, setInstances] = useState<WorkflowInstance[]>([]);
  const [executions, setExecutions] = useState<ExecutionLog[]>([]);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');
  const [expandedId, setExpandedId] = useState<string | null>(null);

  // Pagination & Filtering state
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [selectedWorkflowKey, setSelectedWorkflowKey] = useState('ALL');
  const [selectedStatus, setSelectedStatus] = useState('ALL');

  // Resume dialog state
  const [resumeDialogOpen, setResumeDialogOpen] = useState(false);
  const [resumeInstanceId, setResumeInstanceId] = useState<string | null>(null);
  const [additionalContextJson, setAdditionalContextJson] = useState('{\n  \n}');
  const [resuming, setResuming] = useState(false);

  // Revert status logs state
  const [revertLogs, setRevertLogs] = useState<Record<string, any[]>>({});
  const [loadingRevert, setLoadingRevert] = useState<Record<string, boolean>>({});

  const fetchRevertStatus = async (instanceId: string) => {
    setLoadingRevert(prev => ({ ...prev, [instanceId]: true }));
    try {
      const res = await fetch(`/api/instances/${instanceId}/revert-status`);
      if (!res.ok) throw new Error('Failed to fetch revert status history');
      const data = await res.json();
      setRevertLogs(prev => ({ ...prev, [instanceId]: data }));
    } catch (err: any) {
      console.error('Revert status fetch error:', err.message);
    } finally {
      setLoadingRevert(prev => ({ ...prev, [instanceId]: false }));
    }
  };

  const fetchInstancesAndExecutions = async (
    targetPage: number = page,
    targetSize: number = size,
    wfKey: string = selectedWorkflowKey,
    status: string = selectedStatus,
    searchTerm: string = search
  ) => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      params.append('page', String(targetPage));
      params.append('size', String(targetSize));

      if (wfKey && wfKey !== 'ALL') {
        params.append('workflowKey', wfKey);
      }
      if (status && status !== 'ALL') {
        params.append('status', status);
      }
      if (searchTerm && searchTerm.trim() !== '') {
        params.append('search', searchTerm.trim());
      }

      // Fetch instances
      const instRes = await fetch(`/api/instances?${params.toString()}`);
      if (!instRes.ok) throw new Error('Failed to fetch workflow instances');
      const instData = await instRes.json();
      setInstances(instData.content || []);
      setTotalElements(instData.totalElements || 0);
      setTotalPages(instData.totalPages || 0);

      // Fetch executions
      const execRes = await fetch('/api/executions');
      if (execRes.ok) {
        const execData = await execRes.json();
        setExecutions(execData);
      }
    } catch (err: any) {
      onShowNotification(err.message, 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchInstancesAndExecutions(page, size, selectedWorkflowKey, selectedStatus, search);
  }, [page, size, selectedWorkflowKey, selectedStatus, search]);

  const handleToggleExpand = (id: string) => {
    const nextState = expandedId === id ? null : id;
    setExpandedId(nextState);
    if (nextState) {
      fetchRevertStatus(id);
    }
  };

  const handleOpenResume = (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    setResumeInstanceId(id);
    setResumeDialogOpen(true);
  };

  const handleCloseResume = () => {
    setResumeDialogOpen(false);
    setResumeInstanceId(null);
    setAdditionalContextJson('{\n  \n}');
  };

  const handleResumeSubmit = async () => {
    if (!resumeInstanceId) return;
    let payload = {};
    try {
      payload = JSON.parse(additionalContextJson);
    } catch (e) {
      onShowNotification('Invalid additional context JSON format', 'error');
      return;
    }

    setResuming(true);
    try {
      const res = await fetch(`/api/instances/${resumeInstanceId}/resume`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      if (!res.ok) throw new Error('Failed to resume workflow instance');
      onShowNotification('Workflow instance resumed successfully', 'success');
      handleCloseResume();
      await fetchInstancesAndExecutions();
    } catch (err: any) {
      onShowNotification(err.message, 'error');
    } finally {
      setResuming(false);
    }
  };

  const handleLaunchReplay = (run: ExecutionLog) => {
    // Find matching workflow and version
    const matchedWf = workflows.find(w => w.key === run.workflowKey);
    if (matchedWf && matchedWf.versions) {
      const matchedVer = matchedWf.versions.find(v => v.id === run.versionId);
      if (matchedVer) {
        setReplayVersion(matchedVer);
      }
    }
    setCurrentExecution(run);
    setView('replay');
  };

  const handleDeleteInstance = async (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!window.confirm('Are you sure you want to delete this stateful workflow instance?')) return;
    try {
      const res = await fetch(`/api/instances/${id}`, { method: 'DELETE' });
      if (!res.ok) throw new Error('Failed to delete instance');
      onShowNotification('Instance deleted successfully', 'success');
      await fetchInstancesAndExecutions();
    } catch (err: any) {
      onShowNotification(err.message, 'error');
    }
  };



  const accentColor = '#6366f1'; // Premium indigo accent for instances

  const getStatusChip = (status: string) => {
    let icon = <PlayArrowIcon style={{ fontSize: 14 }} />;
    let bgColor = 'rgba(255,255,255,0.06)';
    let textColor = '#fff';
    
    switch (status.toUpperCase()) {
      case 'RUNNING':
        icon = <PlayArrowIcon style={{ fontSize: 14 }} />;
        bgColor = 'rgba(6, 182, 212, 0.12)';
        textColor = '#06b6d4';
        break;
      case 'WAITING':
        icon = <PauseIcon style={{ fontSize: 14 }} />;
        bgColor = 'rgba(245, 158, 11, 0.12)';
        textColor = '#f59e0b';
        break;
      case 'COMPLETED':
        icon = <CheckCircleIcon style={{ fontSize: 14 }} />;
        bgColor = 'rgba(16, 185, 129, 0.12)';
        textColor = '#10b981';
        break;
      case 'FAILED':
        icon = <ErrorIcon style={{ fontSize: 14 }} />;
        bgColor = 'rgba(239, 68, 68, 0.12)';
        textColor = '#ef4444';
        break;
      case 'TERMINATED':
        icon = <CancelIcon style={{ fontSize: 14 }} />;
        bgColor = 'rgba(100, 116, 139, 0.12)';
        textColor = '#64748b';
        break;
    }

    return (
      <Chip
        label={status}
        size="small"
        icon={icon}
        sx={{
          height: 24, fontSize: '11px', fontWeight: 800,
          bgcolor: bgColor, color: textColor,
          border: `1px solid ${textColor}20`,
          '& .MuiChip-icon': { color: 'inherit', mr: -0.2 }
        }}
      />
    );
  };

  return (
    <Box sx={{ bgcolor: 'background.default', minHeight: '92vh', py: 4, color: 'text.primary', transition: 'background-color 0.25s ease-in-out, color 0.25s ease-in-out' }}>
      <Container maxWidth="lg">
        {/* Title bar */}
        <Box sx={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', mb: 4, gap: 2 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <IconButton onClick={() => goBack()} sx={{ color: accentColor, border: `1px solid rgba(99,102,241,0.2)` }}>
              <ArrowBackIcon />
            </IconButton>
            <Box>
              <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: '-0.5px' }}>
                Workflow Instances
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Monitor stateful transaction loops, inspect lazy variables context, and resume waiting processes.
              </Typography>
            </Box>
          </Box>
          <IconButton onClick={() => fetchInstancesAndExecutions()} sx={{ color: accentColor, border: `1px solid rgba(99,102,241,0.2)` }}>
            <RefreshIcon />
          </IconButton>
        </Box>

        {/* Filter Panel */}
        <Paper sx={{
          p: 3, mb: 4,
          bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider', borderRadius: 3
        }}>
          <Grid container spacing={2} sx={{ alignItems: 'center' }}>
            {/* Search Input */}
            <Grid size={{ xs: 12, md: 4 }}>
              <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', letterSpacing: '0.5px', display: 'block', mb: 1 }}>
                SEARCH INSTANCE
              </Typography>
              <Box sx={{
                p: '4px 12px', display: 'flex', alignItems: 'center',
                bgcolor: 'background.default', border: '1px solid', borderColor: 'divider', borderRadius: 2
              }}>
                <SearchIcon sx={{ color: 'text.secondary', mr: 1, fontSize: 20 }} />
                <InputBase
                  placeholder="ID, key, or current node..."
                  value={search}
                  onChange={(e) => {
                    setSearch(e.target.value);
                    setPage(0);
                  }}
                  sx={{ ml: 1, flex: 1, color: 'text.primary', fontSize: '14px' }}
                />
              </Box>
            </Grid>

            {/* Workflow Filter Dropdown */}
            <Grid size={{ xs: 12, sm: 6, md: 3 }}>
              <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', letterSpacing: '0.5px', display: 'block', mb: 1 }}>
                FILTER BY WORKFLOW
              </Typography>
              <FormControl fullWidth size="small">
                <Select
                  value={selectedWorkflowKey}
                  onChange={(e) => {
                    setSelectedWorkflowKey(e.target.value as string);
                    setPage(0);
                  }}
                  sx={{
                    bgcolor: 'background.default',
                    borderRadius: 2,
                    fontSize: '14px',
                    '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' }
                  }}
                >
                  <MenuItem value="ALL" sx={{ fontSize: '14px' }}>All Workflows</MenuItem>
                  {workflows.map(w => (
                    <MenuItem key={w.key} value={w.key} sx={{ fontSize: '14px' }}>
                      {w.name} ({w.key})
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Grid>

            {/* Status Filter Dropdown */}
            <Grid size={{ xs: 12, sm: 6, md: 3 }}>
              <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', letterSpacing: '0.5px', display: 'block', mb: 1 }}>
                FILTER BY STATUS
              </Typography>
              <FormControl fullWidth size="small">
                <Select
                  value={selectedStatus}
                  onChange={(e) => {
                    setSelectedStatus(e.target.value as string);
                    setPage(0);
                  }}
                  sx={{
                    bgcolor: 'background.default',
                    borderRadius: 2,
                    fontSize: '14px',
                    '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' }
                  }}
                >
                  <MenuItem value="ALL" sx={{ fontSize: '14px' }}>All Statuses</MenuItem>
                  <MenuItem value="RUNNING" sx={{ fontSize: '14px' }}>RUNNING</MenuItem>
                  <MenuItem value="WAITING" sx={{ fontSize: '14px' }}>WAITING</MenuItem>
                  <MenuItem value="COMPLETED" sx={{ fontSize: '14px' }}>COMPLETED</MenuItem>
                  <MenuItem value="FAILED" sx={{ fontSize: '14px' }}>FAILED</MenuItem>
                  <MenuItem value="TERMINATED" sx={{ fontSize: '14px' }}>TERMINATED</MenuItem>
                </Select>
              </FormControl>
            </Grid>

            {/* Actions (Reset) */}
            <Grid size={{ xs: 12, md: 2 }} sx={{ display: 'flex', gap: 1, alignSelf: 'flex-end', height: 40 }}>
              <Button
                variant="outlined"
                fullWidth
                onClick={() => {
                  setSearch('');
                  setSelectedWorkflowKey('ALL');
                  setSelectedStatus('ALL');
                  setPage(0);
                }}
                sx={{
                  borderRadius: 2,
                  fontWeight: 700,
                  fontSize: '12px',
                  borderColor: 'divider',
                  color: 'text.secondary',
                  '&:hover': {
                    borderColor: accentColor,
                    color: accentColor,
                    bgcolor: 'rgba(99,102,241,0.04)'
                  }
                }}
              >
                Clear Filters
              </Button>
            </Grid>
          </Grid>
        </Paper>

        {/* Loading / Content */}
        {loading && instances.length === 0 ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 12 }}>
            <CircularProgress color="secondary" />
          </Box>
        ) : instances.length === 0 ? (
          <Box sx={{
            p: 8, border: '1px dashed', borderColor: 'divider', borderRadius: 2,
            textAlign: 'center', bgcolor: 'background.paper'
          }}>
            <Typography variant="body1" color="text.secondary">
              No workflow instances found matching your search.
            </Typography>
          </Box>
        ) : (
          <Grid container spacing={3}>
            {instances.map((inst) => {
              const isExpanded = expandedId === inst.id;
              const isWaiting = inst.status.toUpperCase() === 'WAITING';
              
              // Find matching execution runs for this instance
              const relatedRuns = executions.filter(e => e.instanceId === inst.id);

              return (
                <Grid size={{ xs: 12 }} key={inst.id}>
                  <Card sx={{
                    bgcolor: 'background.paper',
                    border: '1px solid',
                    borderColor: isExpanded ? `${accentColor}50` : 'divider',
                    borderRadius: 2,
                    transition: 'border-color 0.2s',
                  }}>
                    <CardContent sx={{ p: 3, cursor: 'pointer' }} onClick={() => handleToggleExpand(inst.id)}>
                      <Box sx={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', gap: 2 }}>
                        {/* Summary Column */}
                        <Box sx={{ flexGrow: 1 }}>
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 1 }}>
                            <Typography variant="h6" sx={{ fontWeight: 800, color: '#cbd5e1' }}>
                              {inst.workflowKey}
                            </Typography>
                            <Typography variant="caption" sx={{ bgcolor: 'rgba(255,255,255,0.04)', px: 1, py: 0.2, borderRadius: 1, color: 'text.secondary', fontWeight: 600 }}>
                              v{inst.versionNumber}
                            </Typography>
                            {getStatusChip(inst.status)}
                          </Box>
                          <Typography variant="caption" sx={{ fontFamily: 'monospace', color: 'text.secondary', display: 'block', mb: 0.5 }}>
                            Instance ID: {inst.id}
                          </Typography>
                          {inst.currentNodeId && (
                            <Typography variant="body2" sx={{ color: isWaiting ? '#f59e0b' : 'text.secondary', fontWeight: 600 }}>
                              Current Position: {inst.currentNodeLabel || inst.currentNodeId} ({inst.currentNodeId})
                            </Typography>
                          )}
                        </Box>

                        {/* Action Column */}
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }} onClick={(e) => e.stopPropagation()}>
                          {isWaiting && (
                            <Button
                              variant="contained" size="small" startIcon={<PlayArrowIcon />}
                              onClick={(e) => handleOpenResume(inst.id, e)}
                              sx={{
                                background: `linear-gradient(135deg, ${accentColor} 0%, #4f46e5 100%)`,
                                fontWeight: 700,
                                px: 2, py: 0.75,
                                fontSize: '11px',
                                boxShadow: `0 4px 10px rgba(99,102,241,0.2)`
                              }}
                            >
                              Resume Instance
                            </Button>
                          )}
                          <Button
                            variant="outlined" size="small" color="error"
                            onClick={(e) => handleDeleteInstance(inst.id, e)}
                            sx={{ borderColor: 'rgba(239, 68, 68, 0.2)', fontSize: '11px', py: 0.75 }}
                          >
                            Delete
                          </Button>
                          <IconButton onClick={() => handleToggleExpand(inst.id)} sx={{ color: 'text.secondary' }}>
                            {isExpanded ? <KeyboardArrowUpIcon /> : <KeyboardArrowDownIcon />}
                          </IconButton>
                        </Box>
                      </Box>

                      {/* Expandable Details */}
                      <Collapse in={isExpanded} timeout="auto" unmountOnExit sx={{ mt: 3 }}>
                        <Divider sx={{ borderColor: 'rgba(255,255,255,0.06)', mb: 3 }} />
                        <Grid container spacing={4}>
                          {/* Left Panel: Variables Context */}
                          <Grid size={{ xs: 12, md: 4 }}>
                            <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1, color: '#94a3b8' }}>
                              Variables Context Map (Lazy State Snapshot)
                            </Typography>
                            <Paper sx={{
                              bgcolor: 'background.default', p: 2, borderRadius: 1.5,
                              border: '1px solid', borderColor: 'divider',
                              maxHeight: 300, overflowY: 'auto'
                            }}>
                              <pre style={{
                                margin: 0,
                                fontFamily: 'monospace',
                                fontSize: '11px',
                                color: '#cbd5e1',
                                whiteSpace: 'pre-wrap',
                                wordBreak: 'break-all'
                              }}>
                                {JSON.stringify(inst.context, null, 2)}
                              </pre>
                            </Paper>
                          </Grid>

                          {/* Middle Panel: Bucket Traversal Timeline */}
                          <Grid size={{ xs: 12, md: 4 }}>
                            <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1, color: '#94a3b8' }}>
                              Bucket Traversal &amp; Approvals
                            </Typography>
                            {loadingRevert[inst.id] ? (
                              <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
                                <CircularProgress size={20} color="secondary" />
                              </Box>
                            ) : !revertLogs[inst.id] || revertLogs[inst.id].length === 0 ? (
                              <Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic', py: 2 }}>
                                No external bucket approvals or revert entries logged.
                              </Typography>
                            ) : (
                              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5, maxHeight: 300, overflowY: 'auto', pr: 0.5 }}>
                                {revertLogs[inst.id].map((log: any) => {
                                  let statusColor = '#cbd5e1';
                                  let bgStatusColor = 'rgba(255,255,255,0.06)';
                                  
                                  if (log.status === 'PENDING') {
                                    statusColor = '#f59e0b';
                                    bgStatusColor = 'rgba(245,158,11,0.12)';
                                  } else if (log.status === 'COMPLETED') {
                                    statusColor = '#10b981';
                                    bgStatusColor = 'rgba(16,185,129,0.12)';
                                  } else if (log.status === 'REVERTED') {
                                    statusColor = '#8b5cf6';
                                    bgStatusColor = 'rgba(139,92,246,0.12)';
                                  }

                                  let deps: string[] = [];
                                  if (log.dependencyBucketIds) {
                                    try {
                                      deps = JSON.parse(log.dependencyBucketIds);
                                    } catch {
                                      deps = [log.dependencyBucketIds];
                                    }
                                  }

                                  return (
                                    <Paper key={log.id} sx={{
                                      p: 1.5, bgcolor: 'background.default', borderRadius: 1.5,
                                      border: '1px solid', borderColor: 'divider',
                                      display: 'flex', flexDirection: 'column', gap: 1
                                    }}>
                                      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                        <Typography variant="body2" sx={{ fontWeight: 800, color: '#f1f5f9', fontSize: '12px' }}>
                                          {log.bucketName} ({log.bucketId})
                                        </Typography>
                                        <Chip label={log.status} size="small" sx={{
                                          height: 18, fontSize: '8px', fontWeight: 800,
                                          color: statusColor, bgcolor: bgStatusColor, border: `1px solid ${statusColor}20`
                                        }} />
                                      </Box>
                                      
                                      {log.previousStepId && (
                                        <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', fontSize: '10px' }}>
                                          Chained previous step ID: <span style={{ fontFamily: 'monospace' }}>{log.previousStepId.substring(0, 8)}...</span>
                                        </Typography>
                                      )}

                                      {deps.length > 0 && (
                                        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5, alignItems: 'center' }}>
                                          <Typography variant="caption" sx={{ color: 'text.secondary', fontSize: '10px' }}>Deps:</Typography>
                                          {deps.map((dep, dIdx) => (
                                            <Chip key={dIdx} label={dep} size="small" sx={{ height: 16, fontSize: '8px', bgcolor: 'rgba(99,102,241,0.1)', color: '#6366f1', border: '1px solid rgba(99,102,241,0.2)' }} />
                                          ))}
                                        </Box>
                                      )}

                                      {log.resolvedBy && (
                                        <Box sx={{ mt: 0.5, p: 0.8, bgcolor: 'background.default', borderRadius: 1 }}>
                                          <Typography variant="caption" sx={{ display: 'block', color: 'text.secondary', fontSize: '10px' }}>
                                            Resolved by: <strong>{log.resolvedBy}</strong>
                                          </Typography>
                                          {log.resolutionNotes && (
                                            <Typography variant="caption" sx={{ color: '#cbd5e1', display: 'block', fontStyle: 'italic', mt: 0.5, fontSize: '10px' }}>
                                              &ldquo;{log.resolutionNotes}&rdquo;
                                            </Typography>
                                          )}
                                        </Box>
                                      )}
                                      
                                      <Typography variant="caption" color="text.secondary" sx={{ fontSize: '9px', display: 'block', mt: 0.5 }}>
                                        Created: {new Date(log.createdAt).toLocaleString()}
                                        {log.completedAt && ` · Resolved: ${new Date(log.completedAt).toLocaleString()}`}
                                      </Typography>
                                    </Paper>
                                  );
                                })}
                              </Box>
                            )}
                          </Grid>

                          {/* Right Panel: Execution Runs */}
                          <Grid size={{ xs: 12, md: 4 }}>
                            <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1, color: '#94a3b8' }}>
                              Execution Trace Segment Runs
                            </Typography>
                            {relatedRuns.length === 0 ? (
                              <Typography variant="body2" color="text.secondary">
                                No logged segment runs recorded.
                              </Typography>
                            ) : (
                              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5, maxHeight: 300, overflowY: 'auto' }}>
                                {relatedRuns.map((run, idx) => (
                                  <Paper key={run.id} sx={{
                                    p: 1.8, bgcolor: 'background.paper', borderRadius: 1.5,
                                    border: '1px solid', borderColor: 'divider',
                                    display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                                    '&:hover': { bgcolor: 'action.hover' }
                                  }}>
                                    <Box>
                                      <Typography variant="body2" sx={{ fontWeight: 700, color: '#cbd5e1', mb: 0.5 }}>
                                        Run segment #{relatedRuns.length - idx}
                                      </Typography>
                                      <Typography variant="caption" sx={{ display: 'block', color: 'text.secondary', fontFamily: 'monospace', mb: 0.5 }}>
                                        Status: <span style={{
                                          color: run.status === 'COMPLETED' ? '#10b981' : run.status === 'WAITING' ? '#f59e0b' : '#ef4444',
                                          fontWeight: 'bold'
                                        }}>{run.status}</span>
                                      </Typography>
                                      <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                                        Triggered: {new Date(run.startedAt).toLocaleString()}
                                      </Typography>
                                    </Box>
                                    <Tooltip title="View Trace Replay">
                                      <IconButton size="small" onClick={() => handleLaunchReplay(run)} sx={{ color: accentColor, border: '1px solid rgba(99,102,241,0.1)' }}>
                                        <VisibilityIcon sx={{ fontSize: 16 }} />
                                      </IconButton>
                                    </Tooltip>
                                  </Paper>
                                ))}
                              </Box>
                            )}
                          </Grid>
                        </Grid>
                      </Collapse>
                    </CardContent>
                  </Card>
                </Grid>
              );
            })}
          </Grid>
        )}

        {/* Pagination Bar */}
        {totalElements > 0 && (
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mt: 4, flexWrap: 'wrap', gap: 2 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
              <Typography variant="body2" color="text.secondary">
                Rows per page:
              </Typography>
              <FormControl size="small" sx={{ minWidth: 70 }}>
                <Select
                  value={size}
                  onChange={(e) => {
                    setSize(Number(e.target.value));
                    setPage(0);
                  }}
                  sx={{
                    bgcolor: 'background.paper',
                    borderRadius: 1.5,
                    fontSize: '13px',
                    height: 32,
                    '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' }
                  }}
                >
                  <MenuItem value={5} sx={{ fontSize: '13px' }}>5</MenuItem>
                  <MenuItem value={10} sx={{ fontSize: '13px' }}>10</MenuItem>
                  <MenuItem value={25} sx={{ fontSize: '13px' }}>25</MenuItem>
                  <MenuItem value={50} sx={{ fontSize: '13px' }}>50</MenuItem>
                </Select>
              </FormControl>
              <Typography variant="body2" color="text.secondary">
                Showing {totalElements === 0 ? 0 : page * size + 1} - {Math.min((page + 1) * size, totalElements)} of {totalElements}
              </Typography>
            </Box>

            <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
              <Button
                variant="outlined"
                size="small"
                disabled={page === 0}
                onClick={() => setPage(prev => Math.max(0, prev - 1))}
                sx={{ borderRadius: 1.5, borderColor: 'divider', fontWeight: 700 }}
              >
                Previous
              </Button>
              {Array.from({ length: totalPages }).map((_, idx) => {
                const isSelected = page === idx;
                if (totalPages > 6 && idx !== 0 && idx !== totalPages - 1 && Math.abs(page - idx) > 1) {
                  if (idx === 1 && page > 2) return <span key={idx} style={{ color: '#94a3b8' }}>...</span>;
                  if (idx === totalPages - 2 && page < totalPages - 3) return <span key={idx} style={{ color: '#94a3b8' }}>...</span>;
                  return null;
                }
                return (
                  <Button
                    key={idx}
                    variant={isSelected ? 'contained' : 'outlined'}
                    size="small"
                    onClick={() => setPage(idx)}
                    sx={{
                      minWidth: 32,
                      width: 32,
                      height: 32,
                      p: 0,
                      borderRadius: 1.5,
                      fontWeight: 700,
                      borderColor: 'divider',
                      bgcolor: isSelected ? accentColor : 'transparent',
                      color: isSelected ? '#fff' : 'text.primary',
                      '&:hover': {
                        bgcolor: isSelected ? accentColor : 'rgba(255,255,255,0.04)'
                      }
                    }}
                  >
                    {idx + 1}
                  </Button>
                );
              })}
              <Button
                variant="outlined"
                size="small"
                disabled={page >= totalPages - 1}
                onClick={() => setPage(prev => Math.min(totalPages - 1, prev + 1))}
                sx={{ borderRadius: 1.5, borderColor: 'divider', fontWeight: 700 }}
              >
                Next
              </Button>
            </Box>
          </Box>
        )}

        {/* Resume Variable Dialog */}
        <Dialog open={resumeDialogOpen} onClose={handleCloseResume} fullWidth maxWidth="sm" slotProps={{
          paper: {
            sx: { bgcolor: 'background.paper', color: 'text.primary', border: '1px solid', borderColor: 'divider' }
          }
        }}>
          <DialogTitle sx={{ fontWeight: 800 }}>Resume Workflow Instance</DialogTitle>
          <DialogContent>
            <Typography variant="body2" sx={{ color: 'text.secondary', mb: 2 }}>
              Input any additional context variables to inject into the execution state before manual resumption. Ensure it is valid JSON.
            </Typography>
            <TextField
              multiline
              rows={8}
              fullWidth
              value={additionalContextJson}
              onChange={(e) => setAdditionalContextJson(e.target.value)}
              sx={{
                '& .MuiOutlinedInput-root': {
                  color: 'text.primary',
                  fontFamily: 'monospace',
                  fontSize: '12px',
                  bgcolor: 'background.default',
                  '& fieldset': { borderColor: 'divider' },
                  '&:hover fieldset': { borderColor: accentColor },
                  '&.Mui-focused fieldset': { borderColor: accentColor },
                }
              }}
            />
          </DialogContent>
          <DialogActions sx={{ p: 3 }}>
            <Button onClick={handleCloseResume} sx={{ color: 'text.secondary' }}>Cancel</Button>
            <Button
              variant="contained" onClick={handleResumeSubmit} disabled={resuming}
              sx={{
                background: `linear-gradient(135deg, ${accentColor} 0%, #4f46e5 100%)`,
                fontWeight: 700
              }}
            >
              {resuming ? <CircularProgress size={20} color="inherit" /> : 'Confirm & Resume'}
            </Button>
          </DialogActions>
        </Dialog>
      </Container>
    </Box>
  );
};
