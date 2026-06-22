import React, { useEffect, useState } from 'react';
import {
  Box, Typography, IconButton, Table, TableBody, TableCell, TableContainer,
  TableHead, TableRow, Chip, CircularProgress, Tooltip
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import RefreshIcon from '@mui/icons-material/Refresh';
import DeleteIcon from '@mui/icons-material/Delete';
import PlayCircleIcon from '@mui/icons-material/PlayCircle';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import { useWorkflowStore } from '../store/workflowStore';
import type { ExecutionLog } from '../store/workflowStore';

interface ExecutionHistoryProps {
  onClose?: () => void;
  onShowNotification: (msg: string, severity: 'success' | 'error') => void;
}

export const ExecutionHistory: React.FC<ExecutionHistoryProps> = ({ onClose, onShowNotification }) => {
  const { executions, setExecutions, setCurrentExecution, setReplayVersion, setView } = useWorkflowStore();
  const [loading, setLoading] = useState(false);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  const fetchExecutions = async () => {
    setLoading(true);
    try {
      const response = await fetch('/api/executions?page=0&size=100');
      if (!response.ok) throw new Error('Failed to fetch executions');
      const data: ExecutionLog[] = await response.json();
      setExecutions(data);
    } catch (err: any) {
      onShowNotification(err.message, 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchExecutions(); }, []);

  const handleDelete = async (id: string) => {
    if (!window.confirm('Delete this execution log?')) return;
    setDeletingId(id);
    try {
      await fetch(`/api/executions/${id}`, { method: 'DELETE' });
      onShowNotification('Execution log deleted.', 'success');
      fetchExecutions();
    } catch (err: any) {
      onShowNotification(err.message, 'error');
    } finally {
      setDeletingId(null);
    }
  };

  const handleReplay = async (exec: ExecutionLog) => {
    try {
      const [versionRes, execRes] = await Promise.all([
        fetch(`/api/workflows/versions/${exec.versionId}`),
        fetch(`/api/executions/${exec.id}`)
      ]);

      if (!versionRes.ok) throw new Error('Version not found');
      if (!execRes.ok) throw new Error('Execution details not found');

      const version = await versionRes.json();
      const fullExec = await execRes.json();

      setReplayVersion(version);
      setCurrentExecution(fullExec);
      setView('replay');
      if (onClose) onClose();
    } catch (err: any) {
      onShowNotification(err.message || 'Could not load workflow details for replay.', 'error');
    }
  };

  const formatDuration = (ms: number) => {
    if (ms < 1000) return `${ms}ms`;
    return `${(ms / 1000).toFixed(1)}s`;
  };

  const formatTime = (iso?: string) => {
    if (!iso) return '—';
    return new Date(iso).toLocaleString();
  };

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: 'calc(100vh - 50px)', bgcolor: 'background.default' }}>
      {/* Header */}
      <Box sx={{
        display: 'flex', alignItems: 'center', p: 2.5,
        borderBottom: '1px solid',
        borderColor: 'divider',
        bgcolor: 'background.paper'
      }}>
        <PlayCircleIcon sx={{ mr: 1.5, color: '#10b981' }} />
        <Box sx={{ flexGrow: 1 }}>
          <Typography variant="h6" sx={{ fontWeight: 800 }}>Execution History</Typography>
          <Typography variant="caption" color="text.secondary">
            {executions.length} execution{executions.length !== 1 ? 's' : ''} recorded
          </Typography>
        </Box>
        <Tooltip title="Refresh">
          <IconButton onClick={fetchExecutions} disabled={loading} size="small" sx={{ mr: 1 }}>
            {loading ? <CircularProgress size={16} /> : <RefreshIcon />}
          </IconButton>
        </Tooltip>
        {onClose && (
          <IconButton onClick={onClose} size="small">
            <CloseIcon />
          </IconButton>
        )}
      </Box>

      {/* Body */}
      <Box sx={{ flexGrow: 1, overflow: 'auto' }}>
        {executions.length === 0 && !loading ? (
          <Box sx={{ textAlign: 'center', py: 8, opacity: 0.5 }}>
            <PlayCircleIcon sx={{ fontSize: 48, color: '#10b981', mb: 2 }} />
            <Typography color="text.secondary">No executions yet. Run a published workflow to see results here.</Typography>
          </Box>
        ) : (
          <TableContainer>
            <Table size="small" stickyHeader>
              <TableHead>
                <TableRow>
                   <TableCell sx={{ bgcolor: 'background.default', fontWeight: 800, fontSize: '10px', letterSpacing: 1, color: 'text.secondary', py: 1 }}>STATUS</TableCell>
                  <TableCell sx={{ bgcolor: 'background.default', fontWeight: 800, fontSize: '10px', letterSpacing: 1, color: 'text.secondary', py: 1 }}>WORKFLOW</TableCell>
                  <TableCell sx={{ bgcolor: 'background.default', fontWeight: 800, fontSize: '10px', letterSpacing: 1, color: 'text.secondary', py: 1 }}>OUTCOME</TableCell>
                  <TableCell sx={{ bgcolor: 'background.default', fontWeight: 800, fontSize: '10px', letterSpacing: 1, color: 'text.secondary', py: 1 }}>STEPS</TableCell>
                  <TableCell sx={{ bgcolor: 'background.default', fontWeight: 800, fontSize: '10px', letterSpacing: 1, color: 'text.secondary', py: 1 }}>DURATION</TableCell>
                  <TableCell sx={{ bgcolor: 'background.default', fontWeight: 800, fontSize: '10px', letterSpacing: 1, color: 'text.secondary', py: 1 }}>STARTED AT</TableCell>
                  <TableCell sx={{ bgcolor: 'background.default', py: 1 }} />
                </TableRow>
              </TableHead>
              <TableBody>
                {executions.map((exec) => (
                  <TableRow
                    key={exec.id}
                    sx={{
                      '&:hover': { bgcolor: 'action.hover' },
                      cursor: 'pointer',
                      borderBottom: '1px solid',
                      borderColor: 'divider'
                    }}
                    onClick={() => handleReplay(exec)}
                  >
                    <TableCell sx={{ py: 1.5 }}>
                      {exec.status === 'COMPLETED'
                        ? <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                            <CheckCircleIcon sx={{ fontSize: 14, color: '#10b981' }} />
                            <Typography variant="caption" sx={{ color: '#10b981', fontWeight: 700 }}>COMPLETED</Typography>
                          </Box>
                        : <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                            <ErrorIcon sx={{ fontSize: 14, color: '#ef4444' }} />
                            <Typography variant="caption" sx={{ color: '#ef4444', fontWeight: 700 }}>FAILED</Typography>
                          </Box>}
                    </TableCell>
                    <TableCell sx={{ py: 1.5 }}>
                      <Typography variant="caption" sx={{ fontWeight: 700 }}>{exec.workflowKey}</Typography>
                      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', fontSize: '9px' }}>
                        v{exec.versionNumber} · {exec.contextId?.substring(0, 8)}...
                      </Typography>
                    </TableCell>
                    <TableCell sx={{ py: 1.5 }}>
                      {exec.outcomeNodeLabel
                        ? <Chip label={exec.outcomeNodeLabel} size="small" sx={{ height: 18, fontSize: '9px', fontWeight: 700, bgcolor: 'rgba(168,85,247,0.15)', color: '#c084fc' }} />
                        : <Typography variant="caption" color="text.secondary">—</Typography>}
                    </TableCell>
                    <TableCell sx={{ py: 1.5 }}>
                      <Typography variant="caption" sx={{ fontWeight: 700, color: '#6366f1' }}>
                        {exec.executionTrace?.length || 0}
                      </Typography>
                    </TableCell>
                    <TableCell sx={{ py: 1.5 }}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                        <AccessTimeIcon sx={{ fontSize: 11, color: 'text.secondary' }} />
                        <Typography variant="caption" color="text.secondary">{formatDuration(exec.totalDurationMs)}</Typography>
                      </Box>
                    </TableCell>
                    <TableCell sx={{ py: 1.5 }}>
                      <Typography variant="caption" color="text.secondary">{formatTime(exec.startedAt)}</Typography>
                    </TableCell>
                    <TableCell sx={{ py: 1.5 }} onClick={(e) => e.stopPropagation()}>
                      <Tooltip title="Delete execution log">
                        <IconButton
                          size="small"
                          color="error"
                          onClick={() => handleDelete(exec.id)}
                          disabled={deletingId === exec.id}
                        >
                          {deletingId === exec.id ? <CircularProgress size={14} /> : <DeleteIcon sx={{ fontSize: 14 }} />}
                        </IconButton>
                      </Tooltip>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Box>
    </Box>
  );
};
