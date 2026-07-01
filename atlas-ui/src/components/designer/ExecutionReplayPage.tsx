import React, { useState, useEffect } from 'react';
import {
  Box,
  Typography,
  Button,
  Chip,
  Alert,
  IconButton,
  Tabs,
  Tab,
  TextField,
  InputAdornment,
  Tooltip,
  Select,
  MenuItem,
  FormControl,
  CircularProgress,
  Divider,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import PauseIcon from '@mui/icons-material/Pause';
import SkipNextIcon from '@mui/icons-material/SkipNext';
import SkipPreviousIcon from '@mui/icons-material/SkipPrevious';
import ReplayIcon from '@mui/icons-material/Replay';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import DataObjectIcon from '@mui/icons-material/DataObject';
import ListAltIcon from '@mui/icons-material/ListAlt';
import SearchIcon from '@mui/icons-material/Search';
import PlayCircleIcon from '@mui/icons-material/PlayCircle';
import HubIcon from '@mui/icons-material/Hub';
import { useWorkflowStore } from '../../store/workflowStore';
import type { StepRecord } from '../../store/workflowStore';
import { DesignerCanvas } from './DesignerCanvas';

interface ExecutionReplayPageProps {
  onShowNotification: (msg: string, severity: 'success' | 'error') => void;
}

export const ExecutionReplayPage: React.FC<ExecutionReplayPageProps> = ({ onShowNotification }) => {
  const { currentExecution, replayVersion, setSelectedVersion, setView, goBack } = useWorkflowStore();

  const [activeTraceStep, setActiveTraceStep] = useState<number | null>(0);
  const [isPlaying, setIsPlaying] = useState<boolean>(false);
  const [playbackSpeed, setPlaybackSpeed] = useState<number>(1.0);
  const [tabValue, setTabValue] = useState<number>(0);
  const [searchTerm, setSearchTerm] = useState<string>('');
  const [runtimeGraph, setRuntimeGraph] = useState<any | null>(null);
  const [tasks, setTasks] = useState<any[]>([]);
  const [subscriptions, setSubscriptions] = useState<any[]>([]);
  const [isLoadingTasks, setIsLoadingTasks] = useState<boolean>(false);

  const fetchTasksAndSubscriptions = () => {
    if (!currentExecution?.instanceId) return;
    setIsLoadingTasks(true);
    Promise.all([
      fetch(`/api/instances/${currentExecution.instanceId}/tasks`).then(res => res.ok ? res.json() : []),
      fetch(`/api/instances/${currentExecution.instanceId}/subscriptions`).then(res => res.ok ? res.json() : [])
    ]).then(([tasksData, subsData]) => {
      setTasks(tasksData);
      setSubscriptions(subsData);
      setIsLoadingTasks(false);
    }).catch(err => {
      console.error(err);
      setIsLoadingTasks(false);
    });
  };

  if (!currentExecution || !replayVersion) {
    return (
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh', flexDirection: 'column', gap: 2 }}>
        <Alert severity="warning">No execution selected for replay.</Alert>
        <Button onClick={() => goBack()} startIcon={<ArrowBackIcon />} variant="outlined">
          Back
        </Button>
      </Box>
    );
  }

  // Set the selected version to the replay version so DesignerCanvas can load it
  useEffect(() => {
    setSelectedVersion(replayVersion);
  }, [replayVersion]);

  // Fetch Workflow Instance to obtain the runtime graph
  useEffect(() => {
    if (currentExecution?.instanceId) {
      fetch(`/api/instances/${currentExecution.instanceId}`)
        .then((res) => {
          if (res.ok) return res.json();
          throw new Error('Failed to fetch instance details');
        })
        .then((data) => {
          if (data && data.runtimeGraph) {
            setRuntimeGraph(data.runtimeGraph);
          }
        })
        .catch((err) => {
          console.error('Error fetching runtime graph:', err);
        });
      fetchTasksAndSubscriptions();
    } else {
      setRuntimeGraph(null);
    }
  }, [currentExecution]);

  useEffect(() => {
    if (tabValue === 2) {
      fetchTasksAndSubscriptions();
    }
  }, [tabValue, currentExecution?.instanceId]);

  // Playback timer effect
  useEffect(() => {
    let intervalId: any = null;
    const traceLength = currentExecution.executionTrace?.length || 0;

    if (isPlaying && traceLength > 0) {
      intervalId = setInterval(() => {
        setActiveTraceStep((prev) => {
          if (prev === null) return 0;
          if (prev < traceLength - 1) {
            return prev + 1;
          } else {
            setIsPlaying(false);
            return prev;
          }
        });
      }, 1000 / playbackSpeed);
    }
    return () => {
      if (intervalId) {
        clearInterval(intervalId);
      }
    };
  }, [isPlaying, playbackSpeed, currentExecution.executionTrace]);

  const formatDuration = (ms: number) => ms < 1000 ? `${ms}ms` : `${(ms / 1000).toFixed(1)}s`;

  const trace = currentExecution.executionTrace || [];
  const currentStep: StepRecord | undefined = activeTraceStep !== null ? trace[activeTraceStep] : undefined;

  const handleStepClick = (index: number) => {
    setActiveTraceStep(index);
    setIsPlaying(false);
  };

  const handleReset = () => {
    setActiveTraceStep(0);
    setIsPlaying(false);
  };

  const handleStepBack = () => {
    if (activeTraceStep !== null && activeTraceStep > 0) {
      setActiveTraceStep(activeTraceStep - 1);
    }
    setIsPlaying(false);
  };

  const handleStepForward = () => {
    if (activeTraceStep !== null && activeTraceStep < trace.length - 1) {
      setActiveTraceStep(activeTraceStep + 1);
    }
    setIsPlaying(false);
  };

  const getStatusColor = (status: string) => {
    switch (status?.toUpperCase()) {
      case 'COMPLETED':
        return '#10b981';
      case 'EVALUATED':
        return '#f59e0b';
      case 'ROUTED':
        return '#14b8a6';
      case 'ENTERED':
        return '#6366f1';
      case 'FAILED':
        return '#ef4444';
      case 'SKIPPED':
        return '#6b7280';
      default:
        return '#94a3b8';
    }
  };

  const formatValue = (val: any): React.ReactNode => {
    if (val === null || val === undefined) return <span style={{ color: '#94a3b8' }}>null</span>;
    if (typeof val === 'boolean') return <span style={{ color: val ? '#10b981' : '#ef4444', fontWeight: 600 }}>{val ? 'true' : 'false'}</span>;
    if (typeof val === 'number') return <span style={{ color: '#3b82f6' }}>{val}</span>;
    if (typeof val === 'object') {
      return (
        <pre style={{ margin: 0, fontSize: '11px', fontFamily: 'monospace', color: '#a78bfa', whiteSpace: 'pre-wrap' }}>
          {JSON.stringify(val, null, 2)}
        </pre>
      );
    }
    return <span style={{ color: '#e2e8f0' }}>"{val.toString()}"</span>;
  };

  const filteredContext = Object.entries(currentExecution.inputContext || {}).filter(([key]) =>
    key.toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100vh', overflow: 'hidden' }}>
      {/* Replay info bar */}
      <Box sx={{
        display: 'flex', alignItems: 'center', gap: 2, px: 3, py: 1.5,
        bgcolor: currentExecution.status === 'COMPLETED' ? 'rgba(16,185,129,0.08)' : 'rgba(239,68,68,0.08)',
        borderBottom: '1px solid',
        borderColor: theme => theme.palette.mode === 'dark' ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)',
        zIndex: 10,
        flexShrink: 0,
      }}>
        <IconButton size="small" onClick={() => goBack()} sx={{ color: 'text.secondary' }}>
          <ArrowBackIcon />
        </IconButton>

        {currentExecution.status === 'COMPLETED'
          ? <CheckCircleIcon sx={{ color: '#10b981' }} />
          : <ErrorIcon sx={{ color: '#ef4444' }} />}

        <Box>
          <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
            Execution Replay — {currentExecution.workflowKey}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            Context ID: {currentExecution.contextId}
          </Typography>
        </Box>

        <Box sx={{ display: 'flex', gap: 1.5, ml: 2, flexWrap: 'wrap', alignItems: 'center' }}>
          <Chip
            label={currentExecution.status}
            size="small"
            sx={{
              height: 20, fontSize: '9px', fontWeight: 800,
              bgcolor: currentExecution.status === 'COMPLETED' ? 'rgba(16,185,129,0.2)' : 'rgba(239,68,68,0.2)',
              color: currentExecution.status === 'COMPLETED' ? '#10b981' : '#ef4444',
            }}
          />
          <Typography variant="caption" color="text.secondary" sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
            <AccessTimeIcon sx={{ fontSize: 12 }} />
            {formatDuration(currentExecution.totalDurationMs)}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {trace.length} steps
          </Typography>
          {currentExecution.outcomeNodeLabel && (
            <Chip
              label={`→ ${currentExecution.outcomeNodeLabel}`}
              size="small"
              sx={{ height: 20, fontSize: '9px', fontWeight: 800, bgcolor: 'rgba(168,85,247,0.2)', color: '#c084fc' }}
            />
          )}
        </Box>

        <Box sx={{ ml: 'auto' }}>
          <Button
            size="small"
            variant="outlined"
            startIcon={<PlayCircleIcon />}
            onClick={() => setView('executor')}
            sx={{ borderColor: 'rgba(16,185,129,0.3)', color: '#10b981' }}
          >
            Run Again
          </Button>
        </Box>
      </Box>

      {/* Main content Split */}
      <Box sx={{ display: 'flex', flexGrow: 1, overflow: 'hidden' }}>
        {/* Left Sidebar Pane */}
        <Box sx={{
          width: 360,
          flexShrink: 0,
          display: 'flex',
          flexDirection: 'column',
          borderRight: '1px solid',
          borderColor: 'divider',
          bgcolor: 'background.paper',
          height: '100%',
          overflow: 'hidden'
        }}>
          <Tabs
            value={tabValue}
            onChange={(_, val) => setTabValue(val)}
            variant="fullWidth"
            sx={{ borderBottom: '1px solid', borderColor: 'divider' }}
          >
            <Tab icon={<ListAltIcon sx={{ fontSize: 18 }} />} label="Timeline" sx={{ minHeight: 48 }} />
            <Tab icon={<DataObjectIcon sx={{ fontSize: 18 }} />} label="Context" sx={{ minHeight: 48 }} />
            <Tab icon={<HubIcon sx={{ fontSize: 18 }} />} label="Activity" sx={{ minHeight: 48 }} />
          </Tabs>

          <Box sx={{ flexGrow: 1, overflowY: 'auto', p: 1.5 }}>
            {tabValue === 0 && (
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
                {trace.length === 0 ? (
                  <Alert severity="info">No trace steps recorded.</Alert>
                ) : (
                  trace.map((step, idx) => {
                    const isActive = idx === activeTraceStep;
                    const statusColor = getStatusColor(step.status);
                    return (
                      <Box
                        key={idx}
                        onClick={() => handleStepClick(idx)}
                        sx={{
                          p: 1.5,
                          cursor: 'pointer',
                          borderRadius: '8px',
                          border: '1px solid',
                          borderColor: isActive ? 'primary.main' : 'divider',
                          bgcolor: isActive
                            ? theme => theme.palette.mode === 'dark' ? 'rgba(99,102,241,0.15)' : 'rgba(99,102,241,0.05)'
                            : 'background.default',
                          boxShadow: isActive ? '0 0 12px rgba(99,102,241,0.2)' : 'none',
                          borderLeft: `5px solid ${statusColor}`,
                          '&:hover': {
                            borderColor: isActive ? 'primary.main' : 'text.secondary',
                            bgcolor: isActive
                              ? theme => theme.palette.mode === 'dark' ? 'rgba(99,102,241,0.2)' : 'rgba(99,102,241,0.08)'
                              : 'action.hover'
                          },
                          transition: 'all 0.2s ease-in-out'
                        }}
                      >
                        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 0.5 }}>
                          <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary' }}>
                            Step {idx + 1}
                          </Typography>
                          <Chip
                            label={step.status}
                            size="small"
                            sx={{
                              height: 16,
                              fontSize: '8px',
                              fontWeight: 800,
                              bgcolor: `${statusColor}22`,
                              color: statusColor,
                              border: `1px solid ${statusColor}44`,
                            }}
                          />
                        </Box>
                        
                        <Typography variant="body2" sx={{ fontWeight: 700, mb: 0.5 }}>
                          {step.nodeType}: {step.label || step.nodeId}
                        </Typography>

                        {step.expression && (
                          <Box sx={{
                            mt: 1, p: 0.75, borderRadius: '4px',
                            bgcolor: theme => theme.palette.mode === 'dark' ? 'rgba(0,0,0,0.3)' : 'rgba(0,0,0,0.03)',
                            border: '1px solid', borderColor: 'divider'
                          }}>
                            <Typography variant="caption" sx={{ fontFamily: 'monospace', display: 'block', wordBreak: 'break-all', color: 'text.primary' }}>
                              {step.expression}
                            </Typography>
                            {step.expressionResult !== undefined && (
                              <Box sx={{ display: 'block', mt: 0.5, fontWeight: 700, fontSize: '0.75rem', color: 'text.secondary' }}>
                                Result: {formatValue(step.expressionResult)}
                              </Box>
                            )}
                          </Box>
                        )}

                        {step.notes && (
                          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1, fontStyle: 'italic' }}>
                            {step.notes}
                          </Typography>
                        )}

                        {step.durationMs !== undefined && (
                          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5, textAlign: 'right' }}>
                            Duration: {step.durationMs}ms
                          </Typography>
                        )}
                      </Box>
                    );
                  })
                )}
              </Box>
            )}

            {tabValue === 1 && (
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                <TextField
                  fullWidth
                  size="small"
                  placeholder="Filter keys..."
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  slotProps={{
                    input: {
                      startAdornment: (
                        <InputAdornment position="start">
                          <SearchIcon sx={{ fontSize: 18 }} />
                        </InputAdornment>
                      ),
                    }
                  }}
                />

                {filteredContext.length === 0 ? (
                  <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', mt: 2 }}>
                    No context variables found.
                  </Typography>
                ) : (
                  <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                    {filteredContext.map(([key, val]) => (
                      <Box
                        key={key}
                        sx={{
                          p: 1.25, borderRadius: '6px',
                          border: '1px solid', borderColor: 'divider',
                          bgcolor: 'background.default',
                          display: 'flex', flexDirection: 'column', gap: 0.5,
                          '&:hover': { bgcolor: 'action.hover' }
                        }}
                      >
                        <Typography variant="subtitle2" sx={{ fontFamily: 'monospace', fontWeight: 700, color: '#6366f1', wordBreak: 'break-all' }}>
                          {key}
                        </Typography>
                        <Box sx={{ pl: 1, borderLeft: '2px solid', borderColor: 'divider', py: 0.25 }}>
                          <Box sx={{ fontFamily: 'monospace', fontSize: '0.875rem', color: 'text.primary' }}>
                            {formatValue(val)}
                          </Box>
                        </Box>
                      </Box>
                    ))}
                  </Box>
                )}
              </Box>
            )}

            {tabValue === 2 && (
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
                    Runtime Tasks & Events
                  </Typography>
                  <IconButton size="small" onClick={fetchTasksAndSubscriptions} disabled={isLoadingTasks}>
                    <ReplayIcon fontSize="small" />
                  </IconButton>
                </Box>

                {isLoadingTasks ? (
                  <Box sx={{ display: 'flex', justifyContent: 'center', p: 3 }}>
                    <CircularProgress size={24} />
                  </Box>
                ) : (
                  <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
                    {/* Task Instances */}
                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
                      <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', letterSpacing: 0.5 }}>
                        TASK INSTANCES ({tasks.length})
                      </Typography>
                      {tasks.length === 0 ? (
                        <Typography variant="body2" color="text.secondary">No task instances recorded.</Typography>
                      ) : (
                        tasks.map((task) => (
                          <Box
                            key={task.id}
                            sx={{
                              p: 1.5, borderRadius: '8px',
                              border: '1px solid', borderColor: 'divider',
                              bgcolor: 'background.default',
                              display: 'flex', flexDirection: 'column', gap: 1,
                              borderLeft: `4px solid ${
                                task.status === 'COMPLETED' ? '#10b981' :
                                task.status === 'WAITING' ? '#f59e0b' :
                                task.status === 'FAILED' ? '#ef4444' : '#94a3b8'
                              }`
                            }}
                          >
                            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                              <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                                {task.label}
                              </Typography>
                              <Chip
                                label={task.status}
                                size="small"
                                sx={{
                                  height: 16, fontSize: '8px', fontWeight: 800,
                                  bgcolor: task.status === 'COMPLETED' ? 'rgba(16,185,129,0.1)' : 'rgba(245,158,11,0.1)',
                                  color: task.status === 'COMPLETED' ? '#10b981' : '#f59e0b'
                                }}
                              />
                            </Box>
                            <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
                              <Chip label={task.taskType} size="small" sx={{ height: 16, fontSize: '8px', bgcolor: 'rgba(255,255,255,0.05)' }} />
                              <Typography variant="caption" color="text.secondary">
                                {task.startedAt ? new Date(task.startedAt).toLocaleTimeString() : ''}
                              </Typography>
                            </Box>
                            {task.inputData && Object.keys(task.inputData).length > 0 && (
                              <Box sx={{ mt: 0.5 }}>
                                <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', display: 'block' }}>Inputs</Typography>
                                <pre style={{ margin: 0, fontSize: '10px', fontFamily: 'monospace', color: '#818cf8', whiteSpace: 'pre-wrap', backgroundColor: 'rgba(0,0,0,0.15)', padding: '8px', borderRadius: '4px' }}>
                                  {JSON.stringify(task.inputData, null, 2)}
                                </pre>
                              </Box>
                            )}
                            {task.outputData && Object.keys(task.outputData).length > 0 && (
                              <Box sx={{ mt: 0.5 }}>
                                <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', display: 'block' }}>Outputs</Typography>
                                <pre style={{ margin: 0, fontSize: '10px', fontFamily: 'monospace', color: '#10b981', whiteSpace: 'pre-wrap', backgroundColor: 'rgba(0,0,0,0.15)', padding: '8px', borderRadius: '4px' }}>
                                  {JSON.stringify(task.outputData, null, 2)}
                                </pre>
                              </Box>
                            )}
                          </Box>
                        ))
                      )}
                    </Box>

                    <Divider />

                    {/* Event Subscriptions */}
                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
                      <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', letterSpacing: 0.5 }}>
                        EVENT SUBSCRIPTIONS ({subscriptions.length})
                      </Typography>
                      {subscriptions.length === 0 ? (
                        <Typography variant="body2" color="text.secondary">No event subscriptions.</Typography>
                      ) : (
                        subscriptions.map((sub) => (
                          <Box
                            key={sub.id}
                            sx={{
                              p: 1.5, borderRadius: '8px',
                              border: '1px solid', borderColor: 'divider',
                              bgcolor: 'background.default',
                              display: 'flex', flexDirection: 'column', gap: 1,
                              borderLeft: `4px solid ${sub.status === 'ACTIVE' ? '#3b82f6' : '#6b7280'}`
                            }}
                          >
                            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                              <Typography variant="subtitle2" sx={{ fontWeight: 700, fontFamily: 'monospace', color: '#3b82f6' }}>
                                {sub.eventType}
                              </Typography>
                              <Chip
                                label={sub.status}
                                size="small"
                                sx={{
                                  height: 16, fontSize: '8px', fontWeight: 800,
                                  bgcolor: sub.status === 'ACTIVE' ? 'rgba(59,130,246,0.1)' : 'rgba(107,114,128,0.1)',
                                  color: sub.status === 'ACTIVE' ? '#3b82f6' : '#9ca3af'
                                }}
                              />
                            </Box>
                            <Typography variant="caption" color="text.secondary">
                              Node ID: <code>{sub.targetNodeId}</code>
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              Business Key: <code>{sub.businessKey}</code>
                            </Typography>
                            {sub.filterAttributes && Object.keys(sub.filterAttributes).length > 0 && (
                              <Box sx={{ mt: 0.5 }}>
                                <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', display: 'block' }}>Filters</Typography>
                                <pre style={{ margin: 0, fontSize: '10px', fontFamily: 'monospace', color: '#a78bfa', whiteSpace: 'pre-wrap', backgroundColor: 'rgba(0,0,0,0.15)', padding: '8px', borderRadius: '4px' }}>
                                  {JSON.stringify(sub.filterAttributes, null, 2)}
                                </pre>
                              </Box>
                            )}
                          </Box>
                        ))
                      )}
                    </Box>
                  </Box>
                )}
              </Box>
            )}
          </Box>
        </Box>

        {/* Right Canvas Pane */}
        <Box sx={{ flexGrow: 1, height: '100%', position: 'relative', overflow: 'hidden' }}>
          <DesignerCanvas
            onRefreshWorkflows={async () => {}}
            onShowNotification={onShowNotification}
            traceMode={true}
            executionTrace={trace}
            activeTraceStep={activeTraceStep}
            setActiveTraceStep={setActiveTraceStep}
            hideHeader={true}
            hideTimeline={true}
            runtimeGraph={runtimeGraph}
          />

          {/* Floating Glassmorphic Playback controls */}
          {trace.length > 0 && (
            <Box sx={{
              position: 'absolute',
              bottom: 24,
              left: '50%',
              transform: 'translateX(-50%)',
              backdropFilter: 'blur(16px)',
              backgroundColor: theme => theme.palette.mode === 'dark' ? 'rgba(30, 41, 59, 0.75)' : 'rgba(255, 255, 255, 0.75)',
              border: '1px solid',
              borderColor: theme => theme.palette.mode === 'dark' ? 'rgba(255, 255, 255, 0.1)' : 'rgba(0, 0, 0, 0.1)',
              borderRadius: '50px',
              px: 3,
              py: 1.25,
              display: 'flex',
              alignItems: 'center',
              gap: 2,
              boxShadow: '0 10px 30px -10px rgba(0,0,0,0.3)',
              zIndex: 100,
              maxWidth: '90%',
            }}>
              <Box sx={{ display: 'flex', gap: 0.5, alignItems: 'center' }}>
                <Tooltip title="Reset Replay">
                  <IconButton size="small" onClick={handleReset} sx={{ color: 'text.primary' }}>
                    <ReplayIcon sx={{ fontSize: 20 }} />
                  </IconButton>
                </Tooltip>

                <Tooltip title="Step Back">
                  <span>
                    <IconButton size="small" onClick={handleStepBack} disabled={activeTraceStep === 0 || activeTraceStep === null} sx={{ color: 'text.primary' }}>
                      <SkipPreviousIcon sx={{ fontSize: 22 }} />
                    </IconButton>
                  </span>
                </Tooltip>

                <IconButton
                  onClick={() => setIsPlaying(!isPlaying)}
                  sx={{
                    bgcolor: 'primary.main',
                    color: 'primary.contrastText',
                    '&:hover': { bgcolor: 'primary.dark' },
                    width: 40, height: 40,
                    boxShadow: '0 4px 12px rgba(99,102,241,0.3)',
                    mx: 1
                  }}
                >
                  {isPlaying ? <PauseIcon /> : <PlayArrowIcon />}
                </IconButton>

                <Tooltip title="Step Forward">
                  <span>
                    <IconButton size="small" onClick={handleStepForward} disabled={activeTraceStep === trace.length - 1 || activeTraceStep === null} sx={{ color: 'text.primary' }}>
                      <SkipNextIcon sx={{ fontSize: 22 }} />
                    </IconButton>
                  </span>
                </Tooltip>
              </Box>

              <Box sx={{ borderLeft: '1px solid', borderColor: 'divider', height: 24, mx: 1 }} />

              <Box sx={{ display: 'flex', flexDirection: 'column', minWidth: 100 }}>
                <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary' }}>
                  Step {activeTraceStep !== null ? activeTraceStep + 1 : 0} of {trace.length}
                </Typography>
                <Typography variant="body2" sx={{ fontWeight: 700, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: 180 }}>
                  {currentStep ? `${currentStep.nodeType}: ${currentStep.label || currentStep.nodeId}` : ''}
                </Typography>
              </Box>

              <Box sx={{ borderLeft: '1px solid', borderColor: 'divider', height: 24, mx: 1 }} />

              <FormControl variant="standard" size="small" sx={{ minWidth: 70 }}>
                <Select
                  value={playbackSpeed}
                  onChange={(e) => setPlaybackSpeed(Number(e.target.value))}
                  sx={{
                    fontSize: '13px', fontWeight: 700,
                    '&:before': { borderBottom: 'none' },
                    '&:after': { borderBottom: 'none' },
                    '&:hover:not(.Mui-disabled):before': { borderBottom: 'none' }
                  }}
                >
                  <MenuItem value={0.5}>0.5x</MenuItem>
                  <MenuItem value={1.0}>1.0x</MenuItem>
                  <MenuItem value={2.0}>2.0x</MenuItem>
                  <MenuItem value={5.0}>5.0x</MenuItem>
                </Select>
              </FormControl>
            </Box>
          )}
        </Box>
      </Box>
    </Box>
  );
};
