import React, { useEffect, useState, useCallback } from 'react';
import { Box, Container, Grid, Paper, Typography, Chip, CircularProgress, IconButton, Tooltip, Divider, LinearProgress, Badge, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Collapse, Alert, Button, Dialog, DialogTitle, DialogContent, DialogActions } from '@mui/material';
import { ArrowLeft, RefreshCw, ChevronDown, ChevronUp, CheckCircle, Clock, AlertTriangle, Timer, Users } from 'lucide-react';
import { useWorkflowStore } from '../store/workflowStore.js';
const PRIORITY_COLORS = {
    CRITICAL: '#ff1744',
    HIGH: '#ff6d00',
    MEDIUM: '#ffd600',
    LOW: '#00c853',
};
const PRIORITY_BG = {
    CRITICAL: 'rgba(255,23,68,0.12)',
    HIGH: 'rgba(255,109,0,0.12)',
    MEDIUM: 'rgba(255,214,0,0.12)',
    LOW: 'rgba(0,200,83,0.12)',
};
const STATUS_CHIP_COLOR = {
    PENDING: 'warning',
    IN_REVIEW: 'info',
    RESOLVED: 'success',
};
import { FormControl, Select, MenuItem, Checkbox, ListItemText } from '@mui/material';

const CIRCLE_OPTIONS = [
  { id: 101, label: '101 - MH (Maharashtra)', badge: 'MH', color: '#00A05A' },
  { id: 102, label: '102 - DL (Delhi)', badge: 'DL', color: '#0CADEF' },
  { id: 103, label: '103 - KA (Karnataka)', badge: 'KA', color: '#8b5cf6' },
  { id: 104, label: '104 - TN (Tamil Nadu)', badge: 'TN', color: '#ff6d00' },
  { id: 105, label: '105 - DL_NCR (Delhi NCR)', badge: 'NCR', color: '#d97706' },
  { id: 106, label: '106 - ROWB (West Bengal)', badge: 'WB', color: '#64748b' },
];

export const BucketWorkloadPage = ({ onShowNotification }) => {
    const { goBack, setView, selectedCircleIds } = useWorkflowStore();
    const [workloads, setWorkloads] = useState([]);
    const [loading, setLoading] = useState(false);
    const [expandedBucket, setExpandedBucket] = useState(null);
    const [bucketItems, setBucketItems] = useState({});
    const [loadingItems, setLoadingItems] = useState(null);
    const [selectedCircles, setSelectedCircles] = useState(selectedCircleIds || []);

    useEffect(() => {
        if (selectedCircleIds) {
            setSelectedCircles(selectedCircleIds);
        }
    }, [selectedCircleIds]);
    // Detail Modal states
    const [selectedExecution, setSelectedExecution] = useState(null);
    const [detailsModalOpen, setDetailsModalOpen] = useState(false);
    const [modalLoading, setModalLoading] = useState(false);
    const [instanceDetails, setInstanceDetails] = useState(null);
    const [revertHistory, setRevertHistory] = useState([]);
    const [pendingForm, setPendingForm] = useState(null);
    const fetchExecutionDetails = async (item) => {
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
            let revertData = [];
            if (instanceRes.ok) {
                instanceData = await instanceRes.json();
                setInstanceDetails(instanceData);
            }
            if (revertRes.ok) {
                revertData = await revertRes.json();
                setRevertHistory(revertData);
            }
            const pendingRevert = revertData.find((r) => r.status === 'PENDING' && r.bucketId === item.bucketId);
            if (pendingRevert && pendingRevert.formId) {
                const formRes = await fetch(`/api/forms/${pendingRevert.formId}`);
                if (formRes.ok) {
                    const formData = await formRes.json();
                    setPendingForm(formData);
                }
            }
        }
        catch (err) {
            console.error('Failed to load transaction details', err);
        }
        finally {
            setModalLoading(false);
        }
    };
    const fetchWorkloads = useCallback(async () => {
        setLoading(true);
        try {
            const circleParam = selectedCircles && selectedCircles.length > 0 ? `?circleId=${encodeURIComponent(selectedCircles.join(','))}` : '';
            const res = await fetch(`/api/bucket-executions/workload${circleParam}`);
            if (!res.ok)
                throw new Error('Failed to fetch workload data');
            const data = await res.json();
            setWorkloads(data);
        }
        catch (err) {
            onShowNotification(err.message, 'error');
        }
        finally {
            setLoading(false);
        }
    }, [onShowNotification, selectedCircles]);
    useEffect(() => { fetchWorkloads(); }, [fetchWorkloads]);
    const fetchBucketItems = async (bucketId) => {
        setLoadingItems(bucketId);
        try {
            const res = await fetch(`/api/bucket-executions/bucket/${encodeURIComponent(bucketId)}`);
            if (!res.ok)
                throw new Error('Failed to fetch bucket items');
            const data = await res.json();
            setBucketItems(prev => ({ ...prev, [bucketId]: data }));
        }
        catch (err) {
            onShowNotification(err.message, 'error');
        }
        finally {
            setLoadingItems(null);
        }
    };
    const toggleBucket = async (bucketId) => {
        if (expandedBucket === bucketId) {
            setExpandedBucket(null);
        }
        else {
            setExpandedBucket(bucketId);
            if (!bucketItems[bucketId]) {
                await fetchBucketItems(bucketId);
            }
        }
    };
    const totalPending = workloads.reduce((sum, w) => sum + w.pending, 0);
    const totalSlaBreached = workloads.reduce((sum, w) => sum + w.slaBreached, 0);
    const totalResolved = workloads.reduce((sum, w) => sum + w.resolved, 0);
    return (<Box className="atlas-workload-root" sx={{ bgcolor: 'background.default', minHeight: '92vh', py: 4, color: 'text.primary', transition: 'background-color 0.25s ease-in-out' }}>
      <Container className="atlas-workload-container" maxWidth={false} sx={{ px: { xs: 2, sm: 3, md: 4 } }}>
        {/* Header */}
        <Box className="atlas-workload-header" sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 4 }}>
          <Box className="atlas-workload-header-left" sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <IconButton className="atlas-workload-back-btn" onClick={() => goBack()} sx={{ color: '#2F3043', border: '1px solid #2f304344' }}>
              <ArrowLeft size={18}/>
            </IconButton>
            <Box className="atlas-workload-header-text">
              <Typography className="atlas-workload-title" variant="h5" sx={{ fontWeight: 500, color: 'text.primary' }}>
                Bucket Workload
              </Typography>
              <Typography className="atlas-workload-subtitle" variant="body2" sx={{ color: 'text.secondary', mt: 0.5 }}>
                Live operational view — display active workload states and execution logs
              </Typography>
            </Box>
          </Box>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <FormControl size="small" variant="outlined" sx={{ minWidth: 160 }}>
              <Select
                multiple
                value={selectedCircles}
                onChange={(e) => {
                  const val = typeof e.target.value === 'string' ? e.target.value.split(',') : e.target.value;
                  setSelectedCircles(val);
                }}
                displayEmpty
                renderValue={(selected) => {
                  if (!selected || selected.length === 0) return 'All Circles';
                  return selected.map(id => {
                    const opt = CIRCLE_OPTIONS.find(c => c.id === Number(id));
                    return opt ? opt.badge : id;
                  }).join(', ');
                }}
                sx={{
                  height: 36,
                  fontSize: '13px',
                  fontWeight: 600,
                  bgcolor: 'background.paper',
                  borderRadius: 2,
                  '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' }
                }}
              >
                {CIRCLE_OPTIONS.map((opt) => (
                  <MenuItem key={opt.id} value={opt.id} sx={{ fontSize: '13px' }}>
                    <Checkbox checked={selectedCircles.indexOf(opt.id) > -1} size="small" />
                    <ListItemText primary={opt.label} primaryTypographyProps={{ fontSize: '13px' }} />
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <Tooltip title="Refresh workload data">
              <IconButton className="atlas-workload-refresh-btn" onClick={fetchWorkloads} disabled={loading} sx={{ color: 'primary.main', border: '1px solid', borderColor: 'divider', '&:hover': { background: 'action.hover' } }}>
                <RefreshCw size={18}/>
              </IconButton>
            </Tooltip>
          </Box>
        </Box>

        {/* Top-level KPI bar */}
        <Grid className="atlas-workload-kpi-bar" container spacing={3} sx={{ mb: 4 }}>
          {[
            { label: 'Active Buckets', value: workloads.length, icon: <Users size={24}/>, color: '#818cf8', bg: 'rgba(129,140,248,0.1)' },
            { label: 'Pending Review', value: totalPending, icon: <Clock size={24}/>, color: '#fbbf24', bg: 'rgba(251,191,36,0.1)' },
            { label: 'SLA Breached', value: totalSlaBreached, icon: <AlertTriangle size={24}/>, color: '#f87171', bg: 'rgba(248,113,113,0.1)', alert: totalSlaBreached > 0 },
            { label: 'Total Resolved', value: totalResolved, icon: <CheckCircle size={24}/>, color: '#34d399', bg: 'rgba(52,211,153,0.1)' },
          ].map((kpi, i) => (<Grid key={i} item xs={12} sm={6} md={3}>
              <Paper className="atlas-workload-kpi-card" sx={{ p: 3, display: 'flex', alignItems: 'center', gap: 2, bgcolor: 'background.paper', borderRadius: 2, border: '1px solid', borderColor: 'divider', position: 'relative', overflow: 'hidden' }}>
                <Box sx={{ p: 1.5, borderRadius: 2, bgcolor: kpi.bg, color: kpi.color, display: 'flex' }}>{kpi.icon}</Box>
                <Box>
                  <Typography variant="h5" sx={{ fontWeight: 700 }}>{kpi.value}</Typography>
                  <Typography variant="caption" sx={{ color: 'text.secondary', textTransform: 'uppercase', letterSpacing: 0.5 }}>{kpi.label}</Typography>
                </Box>
                {kpi.alert && (<Box sx={{ position: 'absolute', top: 0, right: 0, p: 1 }}>
                    <AlertTriangle size={16} color={kpi.color}/>
                  </Box>)}
              </Paper>
            </Grid>))}
        </Grid>

        {/* Bucket Cards */}
        {workloads.length === 0 && !loading ? (<Paper className="atlas-workload-empty-paper" sx={{ p: 6, textAlign: 'center', background: 'rgba(30,41,59,0.5)', border: '1px solid rgba(96,165,250,0.1)', borderRadius: 3 }}>
            <Typography className="atlas-workload-empty-title" sx={{ color: '#64748b', fontSize: '1.1rem' }}>No bucket activity yet.</Typography>
            <Typography className="atlas-workload-empty-subtitle" sx={{ color: '#475569', mt: 1 }}>Run workflows that route to BUCKET nodes to see workload here.</Typography>
          </Paper>) : (<Box className="atlas-workload-list" sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            {workloads.map((w) => {
                const isExpanded = expandedBucket === w.bucketId;
                const priorityColor = PRIORITY_COLORS[w.priority] ?? '#60a5fa';
                const priorityBg = PRIORITY_BG[w.priority] ?? 'rgba(96,165,250,0.1)';
                const resolvedPct = w.totalRouted > 0 ? Math.round((w.resolved / w.totalRouted) * 100) : 0;
                return (<Paper className="atlas-workload-bucket-card" key={w.bucketId} sx={{ bgcolor: 'background.paper', border: `1px solid ${isExpanded ? priorityColor : 'divider'}`, borderRadius: 2, overflow: 'hidden', transition: 'border-color 0.2s' }}>
                  {/* Bucket header row */}
                  <Box className="atlas-workload-bucket-header" sx={{ p: 3, display: 'flex', alignItems: 'center', gap: 3, cursor: 'pointer', '&:hover': { background: 'rgba(255,255,255,0.02)' } }} onClick={() => toggleBucket(w.bucketId)}>
                    {/* Priority indicator */}
                    <Box className="atlas-workload-priority-indicator" sx={{ width: 6, height: 56, borderRadius: 3, background: priorityColor, flexShrink: 0 }}/>

                    {/* Name & meta */}
                    <Box className="atlas-workload-bucket-info" sx={{ flex: 1, minWidth: 0 }}>
                      <Box className="atlas-workload-bucket-title-group" sx={{ display: 'flex', alignItems: 'center', gap: 1.5, flexWrap: 'wrap' }}>
                        <Typography className="atlas-workload-bucket-name" variant="subtitle1" sx={{ color: 'text.primary', fontWeight: 500 }}>{w.bucketName}</Typography>
                        <Chip className="atlas-workload-priority-chip" label={w.priority} size="small" sx={{ background: priorityBg, color: priorityColor, border: `1px solid ${priorityColor}44`, fontWeight: 500, fontSize: '0.65rem' }}/>
                        {w.slaHours && (<Chip className="atlas-workload-sla-chip" icon={<Timer size={12}/>} label={`SLA: ${w.slaHours}h`} size="small" sx={{ background: 'action.hover', color: 'text.secondary', fontSize: '0.65rem' }}/>)}
                        {w.ownerGroup && (<Chip className="atlas-workload-owner-chip" icon={<Users size={12}/>} label={w.ownerGroup} size="small" sx={{ background: 'action.hover', color: 'text.secondary', fontSize: '0.65rem' }}/>)}
                      </Box>
                      <Typography className="atlas-workload-bucket-id" variant="caption" sx={{ color: '#475569', fontFamily: 'monospace' }}>{w.bucketId}</Typography>
                      {/* Resolution progress bar */}
                      {w.totalRouted > 0 && (<Box className="atlas-workload-resolution-progress" sx={{ mt: 1.5, display: 'flex', alignItems: 'center', gap: 1 }}>
                          <LinearProgress variant="determinate" value={resolvedPct} sx={{ flex: 1, height: 4, borderRadius: 2, background: 'rgba(255,255,255,0.08)', '& .MuiLinearProgress-bar': { background: 'linear-gradient(90deg, #34d399, #60a5fa)', borderRadius: 2 } }}/>
                          <Typography className="atlas-workload-progress-text" variant="caption" sx={{ color: '#64748b', width: 36, textAlign: 'right' }}>{resolvedPct}%</Typography>
                        </Box>)}
                    </Box>

                    {/* Stats chips */}
                    <Box className="atlas-workload-stats-group" sx={{ display: 'flex', gap: 2, flexShrink: 0, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                      {[
                        { val: w.totalRouted, label: 'Total', color: '#60a5fa' },
                        { val: w.pending, label: 'Pending', color: '#fbbf24' },
                        { val: w.inReview, label: 'In Review', color: '#818cf8' },
                        { val: w.resolved, label: 'Resolved', color: '#34d399' },
                    ].map((s) => (<Box className="atlas-workload-stat-chip" key={s.label} sx={{ textAlign: 'center', minWidth: 44 }}>
                          <Typography className="atlas-workload-stat-val" variant="h6" sx={{ color: s.color, fontWeight: 700, lineHeight: 1 }}>{s.val}</Typography>
                          <Typography className="atlas-workload-stat-label" variant="caption" sx={{ color: '#475569', fontSize: '0.6rem' }}>{s.label}</Typography>
                        </Box>))}
                      {w.slaBreached > 0 && (<Box className="atlas-workload-stat-sla-breach" sx={{ textAlign: 'center', minWidth: 44 }}>
                          <Badge badgeContent={w.slaBreached} color="error">
                            <AlertTriangle size={20} color="#f87171"/>
                          </Badge>
                          <Typography variant="caption" sx={{ display: 'block', color: '#f87171', fontSize: '0.6rem', mt: 0.5 }}>SLA</Typography>
                        </Box>)}
                    </Box>

                    {/* Expand toggle */}
                    <IconButton className="atlas-workload-expand-btn" size="small" sx={{ color: '#64748b', flexShrink: 0 }}>
                      {isExpanded ? <ChevronUp size={20}/> : <ChevronDown size={20}/>}
                    </IconButton>
                  </Box>

                  {/* Expandable items table */}
                  <Collapse className="atlas-workload-collapse" in={isExpanded}>
                    <Divider sx={{ borderColor: 'rgba(255,255,255,0.06)' }}/>
                    <Box className="atlas-workload-table-wrapper" sx={{ p: 2 }}>
                      {loadingItems === w.bucketId ? (<Box className="atlas-workload-table-loading" sx={{ py: 3, textAlign: 'center' }}><CircularProgress size={24}/></Box>) : !bucketItems[w.bucketId] || bucketItems[w.bucketId].length === 0 ? (<Typography className="atlas-workload-table-empty" sx={{ color: '#475569', textAlign: 'center', py: 3 }}>No executions routed to this bucket yet.</Typography>) : (<TableContainer className="atlas-workload-table-container">
                          <Table className="atlas-workload-table" size="small">
                            <TableHead className="atlas-workload-table-head">
                              <TableRow>
                                {['Workflow', 'Status', 'SLA', 'Routed At', 'Resolved At', 'Resolved By', 'Resolution Trace'].map(col => (<TableCell key={col} className="atlas-workload-th-cell" sx={{ color: 'text.secondary', borderBottom: '1px solid', borderColor: 'divider', fontSize: '0.7rem', textTransform: 'uppercase', letterSpacing: 0.5 }}>{col}</TableCell>))}
                              </TableRow>
                            </TableHead>
                            <TableBody className="atlas-workload-table-body">
                              {bucketItems[w.bucketId].map((item) => (<TableRow className="atlas-workload-table-row" key={item.id} onClick={() => fetchExecutionDetails(item)} sx={{ cursor: 'pointer', '&:hover': { bgcolor: 'action.hover' } }}>
                                  <TableCell className="atlas-workload-td-key" sx={{ color: 'text.primary', borderBottom: '1px solid', borderColor: 'divider', fontFamily: 'monospace', fontSize: '0.75rem' }}>
                                    {item.workflowKey}
                                  </TableCell>
                                  <TableCell className="atlas-workload-td-status" sx={{ borderBottom: '1px solid', borderColor: 'divider' }}>
                                    <Chip label={item.status} size="small" color={STATUS_CHIP_COLOR[item.status] ?? 'default'} sx={{ fontWeight: 600, fontSize: '0.65rem' }}/>
                                  </TableCell>
                                  <TableCell className="atlas-workload-td-sla" sx={{ borderBottom: '1px solid', borderColor: 'divider' }}>
                                    {item.slaBreached
                                ? <Chip label="BREACHED" size="small" sx={{ background: 'rgba(248,113,113,0.15)', color: '#f87171', fontSize: '0.65rem', fontWeight: 700 }}/>
                                : <Typography sx={{ color: 'text.secondary', fontSize: '0.75rem' }}>OK</Typography>}
                                  </TableCell>
                                  <TableCell className="atlas-workload-td-routed" sx={{ color: 'text.secondary', borderBottom: '1px solid', borderColor: 'divider', fontSize: '0.75rem' }}>
                                    {item.createdAt ? new Date(item.createdAt).toLocaleString() : '—'}
                                  </TableCell>
                                  <TableCell className="atlas-workload-td-resolved" sx={{ color: 'text.secondary', borderBottom: '1px solid', borderColor: 'divider', fontSize: '0.75rem' }}>
                                    {item.resolvedAt ? new Date(item.resolvedAt).toLocaleString() : '—'}
                                  </TableCell>
                                  <TableCell className="atlas-workload-td-by" sx={{ color: 'text.secondary', borderBottom: '1px solid', borderColor: 'divider', fontSize: '0.75rem' }}>
                                    {item.resolvedBy ?? '—'}
                                  </TableCell>
                                  <TableCell className="atlas-workload-td-trace" sx={{ borderBottom: '1px solid', borderColor: 'divider' }}>
                                    {item.status === 'RESOLVED' ? (<Tooltip title={item.resolutionNotes ? `Notes: ${item.resolutionNotes}` : 'Resolved externally'} arrow>
                                        <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5, color: '#34d399', cursor: 'help' }}>
                                          {item.status === 'RESOLVED' && <CheckCircle size={16} color="#34d399"/>}
                                          <Typography variant="caption" sx={{ fontWeight: 600 }}>Closed</Typography>
                                        </Box>
                                      </Tooltip>) : (<Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5, color: '#fbbf24' }}>
                                        <Timer size={14} style={{ color: '#475569' }}/>
                                        <Typography variant="caption" sx={{ fontWeight: 600 }}>Waiting for External Action</Typography>
                                      </Box>)}
                                  </TableCell>
                                </TableRow>))}
                            </TableBody>
                          </Table>
                        </TableContainer>)}

                      {/* Avg resolution time footer */}
                      {w.avgResolutionHours != null && (<Box className="atlas-workload-avg-footer" sx={{ mt: 2, px: 1, display: 'flex', gap: 1, alignItems: 'center' }}>
                          <Clock size={14} style={{ color: '#60a5fa' }}/>
                          <Typography variant="caption" sx={{ color: '#60a5fa' }}>
                            Avg resolution time: <strong>{w.avgResolutionHours.toFixed(1)}h</strong>
                          </Typography>
                        </Box>)}
                    </Box>
                  </Collapse>
                </Paper>);
            })}
          </Box>)}
      </Container>

      {/* Transaction Workload Details Dialog */}
      <Dialog className="atlas-workload-modal" open={detailsModalOpen} onClose={() => setDetailsModalOpen(false)} maxWidth="md" fullWidth>
        <DialogTitle className="atlas-workload-modal-title" sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', pb: 2, borderBottom: '1px solid rgba(255,255,255,0.06)' }}>
          <Box className="atlas-workload-modal-header-text">
            <Typography className="atlas-workload-modal-title-text" variant="h6" sx={{ fontWeight: 700, color: '#f1f5f9' }}>
              Transaction Detail
            </Typography>
            <Typography className="atlas-workload-modal-subtitle-text" variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
              Execution ID: {selectedExecution?.id}
            </Typography>
          </Box>
          {selectedExecution && (<Chip className="atlas-workload-modal-status-chip" label={selectedExecution.status} size="small" color={STATUS_CHIP_COLOR[selectedExecution.status] ?? 'default'} sx={{ fontWeight: 700, fontSize: '0.7rem' }}/>)}
        </DialogTitle>

        <DialogContent className="atlas-workload-modal-content" sx={{ py: 3, display: 'flex', flexDirection: 'column', gap: 3 }}>
          {modalLoading ? (<Box className="atlas-workload-modal-loading" sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', py: 8 }}>
              <CircularProgress color="secondary"/>
            </Box>) : (selectedExecution && (<>
                {/* Pending State Warning and Action Card */}
                {selectedExecution.status === 'PENDING' && (<Paper sx={{
                    p: 2.5,
                    background: 'rgba(245, 158, 11, 0.05)',
                    border: '1px solid rgba(245, 158, 11, 0.2)',
                    borderRadius: 2,
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 1.5
                }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                      <AlertTriangle size={24} style={{ color: '#f87171' }}/>
                      <Typography variant="subtitle2" sx={{ fontWeight: 800, color: '#fbbf24', letterSpacing: '0.5px' }}>
                        WHY IS THIS TRANSACTION PENDING?
                      </Typography>
                    </Box>
                    <Divider sx={{ borderColor: 'rgba(245, 158, 11, 0.15)' }}/>

                    {pendingForm ? (<Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                        <Typography variant="body2" sx={{ color: '#cbd5e1', lineHeight: 1.6 }}>
                          This execution is suspended because it is waiting for manual approval of Customer Form:
                          <strong> {pendingForm.customerName || 'N/A'}</strong> (ID: <code style={{ color: '#f59e0b', fontSize: '0.85rem' }}>{pendingForm.id}</code>).
                        </Typography>
                        <Typography variant="body2" sx={{ color: '#94a3b8' }}>
                          The form status is currently <span style={{ color: '#f59e0b', fontWeight: 700 }}>{pendingForm.formStatus}</span> in the simulator database.
                        </Typography>
                        <Box sx={{ display: 'flex', gap: 2, mt: 1 }}>
                          <Button variant="contained" size="small" onClick={() => {
                        setDetailsModalOpen(false);
                        setView('customerForms');
                    }} sx={{
                        background: 'linear-gradient(135deg, #f59e0b 0%, #d97706 100%)',
                        color: '#000',
                        fontWeight: 700,
                        textTransform: 'none',
                        '&:hover': {
                            background: 'linear-gradient(135deg, #d97706 0%, #b45309 100%)'
                        }
                    }}>
                            Go to Customer Forms Simulator
                          </Button>
                        </Box>
                      </Box>) : (<Box>
                        <Typography variant="body2" sx={{ color: '#cbd5e1', lineHeight: 1.6 }}>
                          This transaction is suspended at bucket <strong>{selectedExecution.bucketName}</strong> ({selectedExecution.bucketId}).
                        </Typography>
                        {revertHistory.length > 0 && revertHistory.some(r => r.status === 'PENDING' && r.dependencyBucketIds) ? (<Box sx={{ mt: 1 }}>
                            <Typography variant="body2" sx={{ color: '#cbd5e1' }}>
                              <strong>Dependencies:</strong> This transaction is waiting for resolution of preceding bucket tasks:
                            </Typography>
                            <Box sx={{ display: 'flex', gap: 1, mt: 1, flexWrap: 'wrap' }}>
                              {JSON.parse(revertHistory.find(r => r.status === 'PENDING' && r.dependencyBucketIds)?.dependencyBucketIds || '[]').map((dep) => (<Chip key={dep} label={dep} size="small" sx={{ bgcolor: 'rgba(255,255,255,0.08)', color: '#94a3b8', fontWeight: 600, fontSize: '0.65rem' }}/>))}
                            </Box>
                          </Box>) : (<Typography variant="body2" sx={{ color: '#94a3b8', mt: 1 }}>
                            It is awaiting external event notifications or manual resolution in this operational queue.
                          </Typography>)}
                      </Box>)}
                  </Paper>)}

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
                      {selectedExecution.slaHours ? (<Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                          <Typography variant="body2" sx={{ fontWeight: 600 }}>
                            {selectedExecution.slaHours} hours
                          </Typography>
                          {selectedExecution.slaBreached && (<Chip label="BREACHED" size="small" sx={{ bgcolor: 'rgba(239, 68, 68, 0.15)', color: '#ef4444', height: 18, fontSize: '0.6rem', fontWeight: 800 }}/>)}
                        </Box>) : (<Typography variant="body2">N/A</Typography>)}
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

                  {selectedExecution.status === 'RESOLVED' && (<>
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
                      {selectedExecution.resolutionNotes && (<Grid size={{ xs: 12 }}>
                          <Paper sx={{ p: 2, bgcolor: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.04)', borderRadius: 2 }}>
                            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
                              RESOLUTION AUDIT NOTES
                            </Typography>
                            <Typography variant="body2" sx={{ color: '#cbd5e1' }}>
                              {selectedExecution.resolutionNotes}
                            </Typography>
                          </Paper>
                        </Grid>)}
                    </>)}
                </Grid>

                {/* Context State Explorer */}
                {instanceDetails && (<Box sx={{ mt: 1 }}>
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1, fontWeight: 700, letterSpacing: '0.5px' }}>
                      ACTIVE TRANSACTION VARIABLES (CONTEXT)
                    </Typography>
                    <Paper sx={{
                    p: 1.5,
                    bgcolor: 'background.default',
                    border: '1px solid',
                    borderColor: 'divider',
                    borderRadius: 1,
                    maxHeight: 200,
                    overflow: 'auto'
                }}>
                      <pre style={{ margin: 0, fontSize: '0.8rem', fontFamily: 'monospace', color: 'text.primary', whiteSpace: 'pre-wrap' }}>
                        {JSON.stringify(instanceDetails.context, null, 2)}
                      </pre>
                    </Paper>
                  </Box>)}
              </>))}
        </DialogContent>

        <DialogActions sx={{ p: 2.5, borderTop: '1px solid rgba(255,255,255,0.06)', display: 'flex', gap: 1 }}>
          <Button onClick={() => setDetailsModalOpen(false)} sx={{
            color: '#94a3b8',
            borderColor: 'rgba(255,255,255,0.1)',
            textTransform: 'none',
            fontWeight: 700,
            borderRadius: 2,
            px: 3,
            '&:hover': {
                bgcolor: 'rgba(255,255,255,0.04)'
            }
        }}>
            Close
          </Button>
        </DialogActions>
      </Dialog>
    </Box>);
};
