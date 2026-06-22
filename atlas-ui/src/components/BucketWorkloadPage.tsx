import React, { useEffect, useState, useCallback } from 'react';
import {
  Box, Container, Grid, Paper, Typography, Chip, CircularProgress,
  IconButton, Tooltip, Divider, LinearProgress,
  Badge,
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  Collapse, Alert, Button, Dialog, DialogTitle, DialogContent, DialogActions
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import RefreshIcon from '@mui/icons-material/Refresh';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import PendingIcon from '@mui/icons-material/Pending';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import GroupIcon from '@mui/icons-material/Group';
import { useWorkflowStore } from '../store/workflowStore';
import type { BucketWorkload, BucketExecution } from '../store/workflowStore';

interface BucketWorkloadPageProps {
  onShowNotification: (message: string, severity: 'success' | 'error') => void;
}

const PRIORITY_COLORS: Record<string, string> = {
  CRITICAL: '#ff1744',
  HIGH: '#ff6d00',
  MEDIUM: '#ffd600',
  LOW: '#00c853',
};

const PRIORITY_BG: Record<string, string> = {
  CRITICAL: 'rgba(255,23,68,0.12)',
  HIGH: 'rgba(255,109,0,0.12)',
  MEDIUM: 'rgba(255,214,0,0.12)',
  LOW: 'rgba(0,200,83,0.12)',
};

const STATUS_CHIP_COLOR: Record<string, 'warning' | 'info' | 'success' | 'default'> = {
  PENDING: 'warning',
  IN_REVIEW: 'info',
  RESOLVED: 'success',
};

export const BucketWorkloadPage: React.FC<BucketWorkloadPageProps> = ({ onShowNotification }) => {
  const { goBack, setView } = useWorkflowStore();
  const [workloads, setWorkloads] = useState<BucketWorkload[]>([]);
  const [loading, setLoading] = useState(false);
  const [expandedBucket, setExpandedBucket] = useState<string | null>(null);
  const [bucketItems, setBucketItems] = useState<Record<string, BucketExecution[]>>({});
  const [loadingItems, setLoadingItems] = useState<string | null>(null);

  // Detail Modal states
  const [selectedExecution, setSelectedExecution] = useState<BucketExecution | null>(null);
  const [detailsModalOpen, setDetailsModalOpen] = useState(false);
  const [modalLoading, setModalLoading] = useState(false);
  const [instanceDetails, setInstanceDetails] = useState<any | null>(null);
  const [revertHistory, setRevertHistory] = useState<any[]>([]);
  const [pendingForm, setPendingForm] = useState<any | null>(null);

  const fetchExecutionDetails = async (item: BucketExecution) => {
    setSelectedExecution(item);
    setDetailsModalOpen(true);
    setModalLoading(true);
    setInstanceDetails(null);
    setRevertHistory([]);
    setPendingForm(null);

    if (!item.instanceId) {
      setModalLoading(false);
      return;
    }

    try {
      const [instanceRes, revertRes] = await Promise.all([
        fetch(`/api/instances/${item.instanceId}`),
        fetch(`/api/instances/${item.instanceId}/revert-status`)
      ]);

      let instanceData = null;
      let revertData: any[] = [];

      if (instanceRes.ok) {
        instanceData = await instanceRes.json();
        setInstanceDetails(instanceData);
      }
      if (revertRes.ok) {
        revertData = await revertRes.json();
        setRevertHistory(revertData);
      }

      const pendingRevert = revertData.find(
        (r: any) => r.status === 'PENDING' && r.bucketId === item.bucketId
      );

      if (pendingRevert && pendingRevert.formId) {
        const formRes = await fetch(`/api/forms/${pendingRevert.formId}`);
        if (formRes.ok) {
          const formData = await formRes.json();
          setPendingForm(formData);
        }
      }
    } catch (err: any) {
      console.error('Failed to load transaction details', err);
    } finally {
      setModalLoading(false);
    }
  };

  const fetchWorkloads = useCallback(async () => {
    setLoading(true);
    try {
      const res = await fetch('/api/bucket-executions/workload');
      if (!res.ok) throw new Error('Failed to fetch workload data');
      const data = await res.json();
      setWorkloads(data);
    } catch (err: any) {
      onShowNotification(err.message, 'error');
    } finally {
      setLoading(false);
    }
  }, [onShowNotification]);

  useEffect(() => { fetchWorkloads(); }, [fetchWorkloads]);

  const fetchBucketItems = async (bucketId: string) => {
    setLoadingItems(bucketId);
    try {
      const res = await fetch(`/api/bucket-executions/bucket/${encodeURIComponent(bucketId)}`);
      if (!res.ok) throw new Error('Failed to fetch bucket items');
      const data = await res.json();
      setBucketItems(prev => ({ ...prev, [bucketId]: data }));
    } catch (err: any) {
      onShowNotification(err.message, 'error');
    } finally {
      setLoadingItems(null);
    }
  };

  const toggleBucket = async (bucketId: string) => {
    if (expandedBucket === bucketId) {
      setExpandedBucket(null);
    } else {
      setExpandedBucket(bucketId);
      if (!bucketItems[bucketId]) {
        await fetchBucketItems(bucketId);
      }
    }
  };

  const totalPending = workloads.reduce((sum, w) => sum + w.pending, 0);
  const totalSlaBreached = workloads.reduce((sum, w) => sum + w.slaBreached, 0);
  const totalResolved = workloads.reduce((sum, w) => sum + w.resolved, 0);

  return (
    <Box sx={{ minHeight: '100vh', background: 'linear-gradient(135deg, #0a0a1a 0%, #0d1b2a 50%, #0a0f1e 100%)', pt: 10, pb: 6 }}>
      <Container maxWidth="xl">
        {/* Header */}
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 4 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <IconButton
              onClick={() => goBack()}
              sx={{ color: '#60a5fa', border: '1px solid rgba(96,165,250,0.3)', '&:hover': { borderColor: '#60a5fa', background: 'rgba(96,165,250,0.1)' } }}
            >
              <ArrowBackIcon />
            </IconButton>
            <Box>
              <Typography variant="h4" sx={{ fontWeight: 700, background: 'linear-gradient(135deg, #60a5fa, #818cf8)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
                Bucket Workload
              </Typography>
              <Typography variant="body2" sx={{ color: '#64748b', mt: 0.5 }}>
                Live operational view — display active workload states and execution logs
              </Typography>
            </Box>
          </Box>
          <Tooltip title="Refresh workload data">
            <IconButton onClick={fetchWorkloads} disabled={loading} sx={{ color: '#60a5fa', border: '1px solid rgba(96,165,250,0.3)', '&:hover': { background: 'rgba(96,165,250,0.1)' } }}>
              <RefreshIcon />
            </IconButton>
          </Tooltip>
        </Box>

        {/* Top-level KPI bar */}
        <Grid container spacing={3} sx={{ mb: 4 }}>
          {[
            { label: 'Active Buckets', value: workloads.length, icon: <GroupIcon />, color: '#818cf8', bg: 'rgba(129,140,248,0.1)' },
            { label: 'Pending Review', value: totalPending, icon: <PendingIcon />, color: '#fbbf24', bg: 'rgba(251,191,36,0.1)' },
            { label: 'SLA Breached', value: totalSlaBreached, icon: <WarningAmberIcon />, color: '#f87171', bg: 'rgba(248,113,113,0.1)', alert: totalSlaBreached > 0 },
            { label: 'Total Resolved', value: totalResolved, icon: <CheckCircleIcon />, color: '#34d399', bg: 'rgba(52,211,153,0.1)' },
          ].map((kpi) => (
            <Grid size={{ xs: 6, md: 3 }} key={kpi.label}>
              <Paper sx={{ p: 3, background: kpi.bg, border: `1px solid ${kpi.color}33`, borderRadius: 3, display: 'flex', alignItems: 'center', gap: 2 }}>
                <Box sx={{ width: 48, height: 48, borderRadius: 2, background: `${kpi.color}22`, display: 'flex', alignItems: 'center', justifyContent: 'center', color: kpi.color }}>
                  {kpi.icon}
                </Box>
                <Box>
                  <Typography variant="h4" sx={{ color: kpi.color, fontWeight: 700, lineHeight: 1 }}>
                    {loading ? '—' : kpi.value}
                  </Typography>
                  <Typography variant="caption" sx={{ color: '#94a3b8' }}>{kpi.label}</Typography>
                  {kpi.alert && kpi.value > 0 && (
                    <Typography variant="caption" sx={{ display: 'block', color: '#f87171', fontSize: '0.65rem' }}>⚠ SLA exceeded!</Typography>
                  )}
                </Box>
              </Paper>
            </Grid>
          ))}
        </Grid>

        {totalSlaBreached > 0 && (
          <Alert severity="error" sx={{ mb: 3, borderRadius: 2, background: 'rgba(248,113,113,0.1)', border: '1px solid rgba(248,113,113,0.3)' }}>
            <strong>{totalSlaBreached} execution{totalSlaBreached > 1 ? 's have' : ' has'} breached SLA</strong> — immediate action required.
          </Alert>
        )}

        {loading && <LinearProgress sx={{ mb: 3, borderRadius: 1 }} />}

        {/* Bucket Cards */}
        {workloads.length === 0 && !loading ? (
          <Paper sx={{ p: 6, textAlign: 'center', background: 'rgba(30,41,59,0.5)', border: '1px solid rgba(96,165,250,0.1)', borderRadius: 3 }}>
            <Typography sx={{ color: '#64748b', fontSize: '1.1rem' }}>No bucket activity yet.</Typography>
            <Typography sx={{ color: '#475569', mt: 1 }}>Run workflows that route to BUCKET nodes to see workload here.</Typography>
          </Paper>
        ) : (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            {workloads.map((w) => {
              const isExpanded = expandedBucket === w.bucketId;
              const priorityColor = PRIORITY_COLORS[w.priority] ?? '#60a5fa';
              const priorityBg = PRIORITY_BG[w.priority] ?? 'rgba(96,165,250,0.1)';
              const resolvedPct = w.totalRouted > 0 ? Math.round((w.resolved / w.totalRouted) * 100) : 0;

              return (
                <Paper key={w.bucketId} sx={{ background: 'rgba(15,23,42,0.8)', border: `1px solid ${isExpanded ? priorityColor + '60' : 'rgba(255,255,255,0.06)'}`, borderRadius: 3, overflow: 'hidden', transition: 'border-color 0.2s' }}>
                  {/* Bucket header row */}
                  <Box
                    sx={{ p: 3, display: 'flex', alignItems: 'center', gap: 3, cursor: 'pointer', '&:hover': { background: 'rgba(255,255,255,0.02)' } }}
                    onClick={() => toggleBucket(w.bucketId)}
                  >
                    {/* Priority indicator */}
                    <Box sx={{ width: 6, height: 56, borderRadius: 3, background: priorityColor, flexShrink: 0 }} />

                    {/* Name & meta */}
                    <Box sx={{ flex: 1, minWidth: 0 }}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, flexWrap: 'wrap' }}>
                        <Typography variant="h6" sx={{ color: '#f1f5f9', fontWeight: 600 }}>{w.bucketName}</Typography>
                        <Chip label={w.priority} size="small" sx={{ background: priorityBg, color: priorityColor, border: `1px solid ${priorityColor}44`, fontWeight: 700, fontSize: '0.65rem' }} />
                        {w.slaHours && (
                          <Chip icon={<AccessTimeIcon sx={{ fontSize: 12 }} />} label={`SLA: ${w.slaHours}h`} size="small" sx={{ background: 'rgba(100,116,139,0.2)', color: '#94a3b8', fontSize: '0.65rem' }} />
                        )}
                        {w.ownerGroup && (
                          <Chip icon={<GroupIcon sx={{ fontSize: 12 }} />} label={w.ownerGroup} size="small" sx={{ background: 'rgba(100,116,139,0.2)', color: '#94a3b8', fontSize: '0.65rem' }} />
                        )}
                      </Box>
                      <Typography variant="caption" sx={{ color: '#475569', fontFamily: 'monospace' }}>{w.bucketId}</Typography>
                      {/* Resolution progress bar */}
                      {w.totalRouted > 0 && (
                        <Box sx={{ mt: 1.5, display: 'flex', alignItems: 'center', gap: 1 }}>
                          <LinearProgress variant="determinate" value={resolvedPct} sx={{ flex: 1, height: 4, borderRadius: 2, background: 'rgba(255,255,255,0.08)', '& .MuiLinearProgress-bar': { background: 'linear-gradient(90deg, #34d399, #60a5fa)', borderRadius: 2 } }} />
                          <Typography variant="caption" sx={{ color: '#64748b', width: 36, textAlign: 'right' }}>{resolvedPct}%</Typography>
                        </Box>
                      )}
                    </Box>

                    {/* Stats chips */}
                    <Box sx={{ display: 'flex', gap: 2, flexShrink: 0, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                      {[
                        { val: w.totalRouted, label: 'Total', color: '#60a5fa' },
                        { val: w.pending, label: 'Pending', color: '#fbbf24' },
                        { val: w.inReview, label: 'In Review', color: '#818cf8' },
                        { val: w.resolved, label: 'Resolved', color: '#34d399' },
                      ].map((s) => (
                        <Box key={s.label} sx={{ textAlign: 'center', minWidth: 44 }}>
                          <Typography variant="h6" sx={{ color: s.color, fontWeight: 700, lineHeight: 1 }}>{s.val}</Typography>
                          <Typography variant="caption" sx={{ color: '#475569', fontSize: '0.6rem' }}>{s.label}</Typography>
                        </Box>
                      ))}
                      {w.slaBreached > 0 && (
                        <Box sx={{ textAlign: 'center', minWidth: 44 }}>
                          <Badge badgeContent={w.slaBreached} color="error">
                            <WarningAmberIcon sx={{ color: '#f87171', fontSize: 20 }} />
                          </Badge>
                          <Typography variant="caption" sx={{ display: 'block', color: '#f87171', fontSize: '0.6rem', mt: 0.5 }}>SLA</Typography>
                        </Box>
                      )}
                    </Box>

                    {/* Expand toggle */}
                    <IconButton size="small" sx={{ color: '#64748b', flexShrink: 0 }}>
                      {isExpanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                    </IconButton>
                  </Box>

                  {/* Expandable items table */}
                  <Collapse in={isExpanded}>
                    <Divider sx={{ borderColor: 'rgba(255,255,255,0.06)' }} />
                    <Box sx={{ p: 2 }}>
                      {loadingItems === w.bucketId ? (
                        <Box sx={{ py: 3, textAlign: 'center' }}><CircularProgress size={24} /></Box>
                      ) : !bucketItems[w.bucketId] || bucketItems[w.bucketId].length === 0 ? (
                        <Typography sx={{ color: '#475569', textAlign: 'center', py: 3 }}>No executions routed to this bucket yet.</Typography>
                      ) : (
                        <TableContainer>
                          <Table size="small">
                            <TableHead>
                              <TableRow>
                                {['Workflow', 'Status', 'SLA', 'Routed At', 'Resolved At', 'Resolved By', 'Resolution Trace'].map(col => (
                                  <TableCell key={col} sx={{ color: '#475569', borderBottom: '1px solid rgba(255,255,255,0.06)', fontSize: '0.7rem', textTransform: 'uppercase', letterSpacing: 0.5 }}>{col}</TableCell>
                                ))}
                              </TableRow>
                            </TableHead>
                            <TableBody>
                              {bucketItems[w.bucketId].map((item) => (
                                <TableRow
                                  key={item.id}
                                  onClick={() => fetchExecutionDetails(item)}
                                  sx={{ cursor: 'pointer', '&:hover': { background: 'rgba(255,255,255,0.04)' } }}
                                >
                                  <TableCell sx={{ color: '#94a3b8', borderBottom: '1px solid rgba(255,255,255,0.04)', fontFamily: 'monospace', fontSize: '0.75rem' }}>
                                    {item.workflowKey}
                                  </TableCell>
                                  <TableCell sx={{ borderBottom: '1px solid rgba(255,255,255,0.04)' }}>
                                    <Chip label={item.status} size="small" color={STATUS_CHIP_COLOR[item.status] ?? 'default'} sx={{ fontWeight: 600, fontSize: '0.65rem' }} />
                                  </TableCell>
                                  <TableCell sx={{ borderBottom: '1px solid rgba(255,255,255,0.04)' }}>
                                    {item.slaBreached
                                      ? <Chip label="BREACHED" size="small" sx={{ background: 'rgba(248,113,113,0.15)', color: '#f87171', fontSize: '0.65rem', fontWeight: 700 }} />
                                      : <Typography sx={{ color: '#64748b', fontSize: '0.75rem' }}>OK</Typography>
                                    }
                                  </TableCell>
                                  <TableCell sx={{ color: '#64748b', borderBottom: '1px solid rgba(255,255,255,0.04)', fontSize: '0.75rem' }}>
                                    {item.createdAt ? new Date(item.createdAt).toLocaleString() : '—'}
                                  </TableCell>
                                  <TableCell sx={{ color: '#64748b', borderBottom: '1px solid rgba(255,255,255,0.04)', fontSize: '0.75rem' }}>
                                    {item.resolvedAt ? new Date(item.resolvedAt).toLocaleString() : '—'}
                                  </TableCell>
                                  <TableCell sx={{ color: '#94a3b8', borderBottom: '1px solid rgba(255,255,255,0.04)', fontSize: '0.75rem' }}>
                                    {item.resolvedBy ?? '—'}
                                  </TableCell>
                                  <TableCell sx={{ borderBottom: '1px solid rgba(255,255,255,0.04)' }}>
                                    {item.status === 'RESOLVED' ? (
                                      <Tooltip title={item.resolutionNotes ? `Notes: ${item.resolutionNotes}` : 'Resolved externally'} arrow>
                                        <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5, color: '#34d399', cursor: 'help' }}>
                                          <CheckCircleIcon sx={{ fontSize: 16 }} />
                                          <Typography variant="caption" sx={{ fontWeight: 600 }}>Closed</Typography>
                                        </Box>
                                      </Tooltip>
                                    ) : (
                                      <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5, color: '#fbbf24' }}>
                                        <AccessTimeIcon sx={{ fontSize: 16 }} />
                                        <Typography variant="caption" sx={{ fontWeight: 600 }}>Waiting for External Action</Typography>
                                      </Box>
                                    )}
                                  </TableCell>
                                </TableRow>
                              ))}
                            </TableBody>
                          </Table>
                        </TableContainer>
                      )}

                      {/* Avg resolution time footer */}
                      {w.avgResolutionHours != null && (
                        <Box sx={{ mt: 2, px: 1, display: 'flex', gap: 1, alignItems: 'center' }}>
                          <AccessTimeIcon sx={{ color: '#60a5fa', fontSize: 14 }} />
                          <Typography variant="caption" sx={{ color: '#60a5fa' }}>
                            Avg resolution time: <strong>{w.avgResolutionHours.toFixed(1)}h</strong>
                          </Typography>
                        </Box>
                      )}
                    </Box>
                  </Collapse>
                </Paper>
              );
            })}
          </Box>
        )}
      </Container>

      {/* Transaction Workload Details Dialog */}
      <Dialog
        open={detailsModalOpen}
        onClose={() => setDetailsModalOpen(false)}
        maxWidth="md"
        fullWidth
        sx={{
          '& .MuiDialog-paper': {
            bgcolor: '#0b1329',
            backgroundImage: 'none',
            border: '1px solid rgba(255,255,255,0.08)',
            borderRadius: 3,
            color: '#f1f5f9',
            boxShadow: '0 24px 48px -12px rgba(0,0,0,0.8)'
          }
        }}
      >
        <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', pb: 2, borderBottom: '1px solid rgba(255,255,255,0.06)' }}>
          <Box>
            <Typography variant="h6" sx={{ fontWeight: 700, color: '#f1f5f9' }}>
              Transaction Detail
            </Typography>
            <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
              Execution ID: {selectedExecution?.id}
            </Typography>
          </Box>
          {selectedExecution && (
            <Chip
              label={selectedExecution.status}
              size="small"
              color={STATUS_CHIP_COLOR[selectedExecution.status] ?? 'default'}
              sx={{ fontWeight: 700, fontSize: '0.7rem' }}
            />
          )}
        </DialogTitle>

        <DialogContent sx={{ py: 3, display: 'flex', flexDirection: 'column', gap: 3 }}>
          {modalLoading ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', py: 8 }}>
              <CircularProgress color="secondary" />
            </Box>
          ) : (
            selectedExecution && (
              <>
                {/* Pending State Warning and Action Card */}
                {selectedExecution.status === 'PENDING' && (
                  <Paper
                    sx={{
                      p: 2.5,
                      background: 'rgba(245, 158, 11, 0.05)',
                      border: '1px solid rgba(245, 158, 11, 0.2)',
                      borderRadius: 2,
                      display: 'flex',
                      flexDirection: 'column',
                      gap: 1.5
                    }}
                  >
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                      <WarningAmberIcon sx={{ color: '#fbbf24', fontSize: 24 }} />
                      <Typography variant="subtitle2" sx={{ fontWeight: 800, color: '#fbbf24', letterSpacing: '0.5px' }}>
                        WHY IS THIS TRANSACTION PENDING?
                      </Typography>
                    </Box>
                    <Divider sx={{ borderColor: 'rgba(245, 158, 11, 0.15)' }} />

                    {pendingForm ? (
                      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                        <Typography variant="body2" sx={{ color: '#cbd5e1', lineHeight: 1.6 }}>
                          This execution is suspended because it is waiting for manual approval of Customer Form:
                          <strong> {pendingForm.customerName || 'N/A'}</strong> (ID: <code style={{ color: '#f59e0b', fontSize: '0.85rem' }}>{pendingForm.id}</code>).
                        </Typography>
                        <Typography variant="body2" sx={{ color: '#94a3b8' }}>
                          The form status is currently <span style={{ color: '#f59e0b', fontWeight: 700 }}>{pendingForm.formStatus}</span> in the simulator database.
                        </Typography>
                        <Box sx={{ display: 'flex', gap: 2, mt: 1 }}>
                          <Button
                            variant="contained"
                            size="small"
                            onClick={() => {
                              setDetailsModalOpen(false);
                              setView('customerForms');
                            }}
                            sx={{
                              background: 'linear-gradient(135deg, #f59e0b 0%, #d97706 100%)',
                              color: '#000',
                              fontWeight: 700,
                              textTransform: 'none',
                              '&:hover': {
                                background: 'linear-gradient(135deg, #d97706 0%, #b45309 100%)'
                              }
                            }}
                          >
                            Go to Customer Forms Simulator
                          </Button>
                        </Box>
                      </Box>
                    ) : (
                      <Box>
                        <Typography variant="body2" sx={{ color: '#cbd5e1', lineHeight: 1.6 }}>
                          This transaction is suspended at bucket <strong>{selectedExecution.bucketName}</strong> ({selectedExecution.bucketId}).
                        </Typography>
                        {revertHistory.length > 0 && revertHistory.some(r => r.status === 'PENDING' && r.dependencyBucketIds) ? (
                          <Box sx={{ mt: 1 }}>
                            <Typography variant="body2" sx={{ color: '#cbd5e1' }}>
                              <strong>Dependencies:</strong> This transaction is waiting for resolution of preceding bucket tasks:
                            </Typography>
                            <Box sx={{ display: 'flex', gap: 1, mt: 1, flexWrap: 'wrap' }}>
                              {JSON.parse(revertHistory.find(r => r.status === 'PENDING' && r.dependencyBucketIds)?.dependencyBucketIds || '[]').map((dep: string) => (
                                <Chip
                                  key={dep}
                                  label={dep}
                                  size="small"
                                  sx={{ bgcolor: 'rgba(255,255,255,0.08)', color: '#94a3b8', fontWeight: 600, fontSize: '0.65rem' }}
                                />
                              ))}
                            </Box>
                          </Box>
                        ) : (
                          <Typography variant="body2" sx={{ color: '#94a3b8', mt: 1 }}>
                            It is awaiting external event notifications or manual resolution in this operational queue.
                          </Typography>
                        )}
                      </Box>
                    )}
                  </Paper>
                )}

                {/* Transaction Metadata Grid */}
                <Grid container spacing={2}>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <Paper sx={{ p: 2, bgcolor: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.04)', borderRadius: 2 }}>
                      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
                        WORKFLOW KEY
                      </Typography>
                      <Typography variant="body2" sx={{ fontFamily: 'monospace', fontWeight: 600 }}>
                        {selectedExecution.workflowKey}
                      </Typography>
                    </Paper>
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <Paper sx={{ p: 2, bgcolor: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.04)', borderRadius: 2 }}>
                      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
                        INSTANCE ID
                      </Typography>
                      <Typography variant="body2" sx={{ fontFamily: 'monospace', color: '#94a3b8', fontSize: '0.8rem' }}>
                        {selectedExecution.instanceId || 'N/A'}
                      </Typography>
                    </Paper>
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6, md: 4 }}>
                    <Paper sx={{ p: 2, bgcolor: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.04)', borderRadius: 2 }}>
                      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
                        ROUTED TIMESTAMP
                      </Typography>
                      <Typography variant="body2">
                        {selectedExecution.createdAt ? new Date(selectedExecution.createdAt).toLocaleString() : '—'}
                      </Typography>
                    </Paper>
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6, md: 4 }}>
                    <Paper sx={{ p: 2, bgcolor: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.04)', borderRadius: 2 }}>
                      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
                        SLA METRIC
                      </Typography>
                      {selectedExecution.slaHours ? (
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                          <Typography variant="body2" sx={{ fontWeight: 600 }}>
                            {selectedExecution.slaHours} hours
                          </Typography>
                          {selectedExecution.slaBreached && (
                            <Chip label="BREACHED" size="small" sx={{ bgcolor: 'rgba(239, 68, 68, 0.15)', color: '#ef4444', height: 18, fontSize: '0.6rem', fontWeight: 800 }} />
                          )}
                        </Box>
                      ) : (
                        <Typography variant="body2">N/A</Typography>
                      )}
                    </Paper>
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6, md: 4 }}>
                    <Paper sx={{ p: 2, bgcolor: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.04)', borderRadius: 2 }}>
                      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
                        RESOLUTION STATUS
                      </Typography>
                      <Typography variant="body2" sx={{ fontWeight: 600, color: selectedExecution.status === 'RESOLVED' ? '#34d399' : '#fbbf24' }}>
                        {selectedExecution.status}
                      </Typography>
                    </Paper>
                  </Grid>

                  {selectedExecution.status === 'RESOLVED' && (
                    <>
                      <Grid size={{ xs: 12, sm: 6 }}>
                        <Paper sx={{ p: 2, bgcolor: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.04)', borderRadius: 2 }}>
                          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
                            RESOLVED BY
                          </Typography>
                          <Typography variant="body2" sx={{ fontWeight: 600 }}>
                            {selectedExecution.resolvedBy || 'System'}
                          </Typography>
                        </Paper>
                      </Grid>
                      <Grid size={{ xs: 12, sm: 6 }}>
                        <Paper sx={{ p: 2, bgcolor: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.04)', borderRadius: 2 }}>
                          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
                            RESOLVED TIMESTAMP
                          </Typography>
                          <Typography variant="body2">
                            {selectedExecution.resolvedAt ? new Date(selectedExecution.resolvedAt).toLocaleString() : '—'}
                          </Typography>
                        </Paper>
                      </Grid>
                      {selectedExecution.resolutionNotes && (
                        <Grid size={{ xs: 12 }}>
                          <Paper sx={{ p: 2, bgcolor: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.04)', borderRadius: 2 }}>
                            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
                              RESOLUTION AUDIT NOTES
                            </Typography>
                            <Typography variant="body2" sx={{ color: '#cbd5e1' }}>
                              {selectedExecution.resolutionNotes}
                            </Typography>
                          </Paper>
                        </Grid>
                      )}
                    </>
                  )}
                </Grid>

                {/* Context State Explorer */}
                {instanceDetails && (
                  <Box sx={{ mt: 1 }}>
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1, fontWeight: 700, letterSpacing: '0.5px' }}>
                      ACTIVE TRANSACTION VARIABLES (CONTEXT)
                    </Typography>
                    <Paper
                      sx={{
                        p: 1.5,
                        bgcolor: '#040814',
                        border: '1px solid rgba(255,255,255,0.05)',
                        borderRadius: 2,
                        maxHeight: 200,
                        overflow: 'auto'
                      }}
                    >
                      <pre style={{ margin: 0, fontSize: '0.8rem', fontFamily: 'monospace', color: '#818cf8', whiteSpace: 'pre-wrap' }}>
                        {JSON.stringify(instanceDetails.context, null, 2)}
                      </pre>
                    </Paper>
                  </Box>
                )}
              </>
            )
          )}
        </DialogContent>

        <DialogActions sx={{ p: 2.5, borderTop: '1px solid rgba(255,255,255,0.06)', display: 'flex', gap: 1 }}>
          <Button
            onClick={() => setDetailsModalOpen(false)}
            sx={{
              color: '#94a3b8',
              borderColor: 'rgba(255,255,255,0.1)',
              textTransform: 'none',
              fontWeight: 700,
              borderRadius: 2,
              px: 3,
              '&:hover': {
                bgcolor: 'rgba(255,255,255,0.04)'
              }
            }}
          >
            Close
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};
