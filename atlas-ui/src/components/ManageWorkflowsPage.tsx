import React, { useState } from 'react';
import { Box, Typography, Button, Card, TableContainer, Table, TableHead, TableBody, TableRow, TableCell, Paper, TextField, InputAdornment, Tooltip, IconButton, Chip, Drawer, Badge, Container } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import SearchIcon from '@mui/icons-material/Search';
import DeleteIcon from '@mui/icons-material/Delete';
import HistoryIcon from '@mui/icons-material/History';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import PlayCircleIcon from '@mui/icons-material/PlayCircle';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import { useWorkflowStore } from '../store/workflowStore';
import type { WorkflowDefinition } from '../store/workflowStore';
import { ExecutionHistory } from './ExecutionHistory';

interface ManageWorkflowsPageProps {
  onOpenCreate: () => void;
  onOpenVersions: (workflow: WorkflowDefinition) => void;
  onDeleteWorkflow: (id: string) => void;
  onShowNotification: (msg: string, severity: 'success' | 'error') => void;
  onOpenCopy: (workflow: WorkflowDefinition) => void;
}

export const ManageWorkflowsPage: React.FC<ManageWorkflowsPageProps> = ({
  onOpenCreate,
  onOpenVersions,
  onDeleteWorkflow,
  onShowNotification,
  onOpenCopy
}) => {
  const { workflows, setView, setSelectedWorkflow, executions } = useWorkflowStore();
  const [searchTerm, setSearchTerm] = useState('');
  const [historyOpen, setHistoryOpen] = useState(false);

  const handleRunWorkflow = (workflow: WorkflowDefinition) => {
    setSelectedWorkflow(workflow);
    setView('executor');
  };

  const filteredWorkflows = workflows.filter(w =>
    w.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
    w.key.toLowerCase().includes(searchTerm.toLowerCase()) ||
    w.description?.toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <Box sx={{ bgcolor: 'background.default', minHeight: '92vh', py: 4, transition: 'background-color 0.25s ease-in-out' }}>
      <Container maxWidth="lg">
        {/* Page Header */}
        <Box sx={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', mb: 4, gap: 2 }}>
          <Box>
            <Typography variant="h4" sx={{ fontWeight: 800, color: 'text.primary', mb: 0.5 }}>
              Workflow Definitions
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Manage, version, and govern enterprise decision structures.
            </Typography>
          </Box>
          <Box sx={{ display: 'flex', gap: 1.5 }}>
            <Badge badgeContent={executions.length} color="success" max={99}>
              <Button
                variant="outlined"
                startIcon={<PlayCircleIcon />}
                onClick={() => setHistoryOpen(true)}
                sx={{
                  borderColor: 'primary.main',
                  color: 'primary.main',
                  '&:hover': {
                    borderColor: 'primary.dark',
                    bgcolor: 'action.hover'
                  }
                }}
              >
                Execution History
              </Button>
            </Badge>
            <Button
              variant="contained"
              color="primary"
              startIcon={<AddIcon />}
              onClick={onOpenCreate}
              sx={{
                fontWeight: 700,
                boxShadow: 2
              }}
            >
              Create Workflow
            </Button>
          </Box>
        </Box>

        {/* Workflow Table Card */}
        <Card sx={{ display: 'flex', flexDirection: 'column', overflow: 'hidden', bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider' }}>
          <Box sx={{ p: 3, display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid', borderColor: 'divider', bgcolor: 'rgba(255,255,255,0.01)' }}>
            <TextField
              size="small"
              placeholder="Search workflows by key, name..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              slotProps={{
                input: {
                  startAdornment: (
                    <InputAdornment position="start">
                      <SearchIcon sx={{ color: 'text.secondary', fontSize: 18 }} />
                    </InputAdornment>
                  ),
                  sx: {
                    borderRadius: 2,
                    bgcolor: 'rgba(0,0,0,0.05)',
                    width: 300,
                    '.MuiOutlinedInput-notchedOutline': {
                      borderColor: 'divider'
                    }
                  }
                }
              }}
            />
          </Box>

          <TableContainer component={Paper} sx={{ bgcolor: 'transparent', boxShadow: 'none', borderRadius: 0 }}>
            <Table>
              <TableHead sx={{ bgcolor: 'rgba(0,0,0,0.02)' }}>
                <TableRow>
                  <TableCell sx={{ fontWeight: 700 }}>Workflow Details</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>Workflow Key</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>Active Version</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>Versions</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>Updated At</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700 }}>Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {filteredWorkflows.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={6} align="center" sx={{ py: 6 }}>
                      <Typography color="text.secondary">No workflows found. Register a workflow to get started.</Typography>
                    </TableCell>
                  </TableRow>
                ) : (
                  filteredWorkflows.map((row) => (
                    <TableRow key={row.id} hover sx={{ '&:hover': { bgcolor: 'rgba(0,0,0,0.01) !important' } }}>
                      <TableCell>
                        <Typography variant="body1" sx={{ fontWeight: 700, color: 'text.primary' }}>
                          {row.name}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {row.description || 'No description provided'}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Chip label={row.key} size="small" sx={{ fontFamily: 'monospace', fontWeight: 600, bgcolor: 'action.selected', border: '1px solid', borderColor: 'divider' }} />
                      </TableCell>
                      <TableCell>
                        {row.activeVersion ? (
                          <Chip label={`v${row.activeVersion}`} color="success" size="small" sx={{ fontWeight: 800 }} />
                        ) : (
                          <Chip label="None" size="small" sx={{ fontStyle: 'italic', bgcolor: 'action.disabledBackground' }} />
                        )}
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" color="text.primary">{row.versions?.length || 0}</Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" color="text.secondary">
                          {new Date(row.updatedAt).toLocaleDateString()}
                        </Typography>
                      </TableCell>
                      <TableCell align="right">
                        <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 1 }}>
                          {row.activeVersion && (
                            <Tooltip title="Run Workflow">
                              <IconButton onClick={() => handleRunWorkflow(row)} size="small"
                                sx={{ border: '1px solid rgba(16,185,129,0.2)', bgcolor: 'rgba(16,185,129,0.05)', color: '#10b981' }}>
                                <PlayArrowIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>
                          )}
                          <Tooltip title="Manage Versions">
                            <IconButton onClick={() => onOpenVersions(row)} color="primary" size="small" sx={{ border: '1px solid', borderColor: 'divider', bgcolor: 'action.hover' }}>
                              <HistoryIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>
                          <Tooltip title="Copy/Clone Workflow">
                            <IconButton onClick={() => onOpenCopy(row)} color="secondary" size="small" sx={{ border: '1px solid', borderColor: 'divider', bgcolor: 'action.hover' }}>
                              <ContentCopyIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>
                          <Tooltip title="Delete Workflow">
                            <IconButton onClick={() => onDeleteWorkflow(row.id)} color="error" size="small" sx={{ border: '1px solid', borderColor: 'divider', bgcolor: 'action.hover' }}>
                              <DeleteIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>
                        </Box>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </TableContainer>
        </Card>

        {/* Execution History Drawer */}
        <Drawer
          anchor="right"
          open={historyOpen}
          onClose={() => setHistoryOpen(false)}
          slotProps={{ paper: { sx: { width: 780, bgcolor: 'background.paper', borderLeft: '1px solid', borderColor: 'divider' } } }}
        >
          <ExecutionHistory
            onClose={() => setHistoryOpen(false)}
            onShowNotification={onShowNotification}
          />
        </Drawer>
      </Container>
    </Box>
  );
};
