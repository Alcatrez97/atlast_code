import React, { useState } from 'react';
import { Box, Typography, Button, Card, TableContainer, Table, TableHead, TableBody, TableRow, TableCell, Paper, TextField, InputAdornment, Tooltip, IconButton, Chip, Drawer, Badge, Container } from '@mui/material';
import { Plus, Search, Trash2, History, Play, PlayCircle, Copy } from 'lucide-react';
import { useWorkflowStore } from '../store/workflowStore.js';
import { ExecutionHistory } from './ExecutionHistory';
export const ManageWorkflowsPage = ({ onOpenCreate, onOpenVersions, onDeleteWorkflow, onShowNotification, onOpenCopy }) => {
    const { workflows, setView, setSelectedWorkflow, executions } = useWorkflowStore();
    const [searchTerm, setSearchTerm] = useState('');
    const [historyOpen, setHistoryOpen] = useState(false);
    const handleRunWorkflow = (workflow) => {
        setSelectedWorkflow(workflow);
        setView('executor');
    };
    const filteredWorkflows = workflows.filter(w => w.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
        w.key.toLowerCase().includes(searchTerm.toLowerCase()) ||
        w.description?.toLowerCase().includes(searchTerm.toLowerCase()));
    return (<Box sx={{ minHeight: '92vh', py: 4, transition: 'background-color 0.25s ease-in-out' }}>
      <Container maxWidth="lg">
        {/* Page Header */}
        <Box sx={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', mb: 4, gap: 2 }}>
          <Box>
            <Typography variant="h5" sx={{ fontWeight: 500, color: 'text.primary', mb: 0.5 }}>
              Workflow Definitions
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Manage, version, and govern enterprise decision structures.
            </Typography>
          </Box>
          <Box sx={{ display: 'flex', gap: 1.5 }}>
            <Badge badgeContent={executions.length} color="success" max={99}>
              <Button variant="outlined" startIcon={<PlayCircle size={18}/>} onClick={() => setHistoryOpen(true)} sx={{
            borderColor: 'primary.main',
            color: 'primary.main',
            '&:hover': {
                borderColor: 'primary.dark',
                bgcolor: 'action.hover'
            }
        }}>
                Execution History
              </Button>
            </Badge>
            <Button variant="contained" color="primary" startIcon={<Plus size={18}/>} onClick={onOpenCreate} sx={{
            fontWeight: 500,
            boxShadow: 'none'
        }}>
              Create Workflow
            </Button>
          </Box>
        </Box>

        {/* Workflow Table Card */}
        <Card sx={{ display: 'flex', flexDirection: 'column', overflow: 'hidden', bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider' }}>
          <Box sx={{ p: 3, display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid', borderColor: 'divider', bgcolor: 'rgba(255,255,255,0.01)' }}>
            <TextField size="small" placeholder="Search workflows by key, name..." value={searchTerm} onChange={(e) => setSearchTerm(e.target.value)} slotProps={{
            input: {
                startAdornment: (<InputAdornment position="start">
                      <Search size={18} color="#82838E"/>
                    </InputAdornment>),
                sx: {
                    borderRadius: 2,
                    bgcolor: 'rgba(0,0,0,0.05)',
                    width: 300,
                    '.MuiOutlinedInput-notchedOutline': {
                        borderColor: 'divider'
                    }
                }
            }
        }}/>
          </Box>

          <TableContainer component={Paper} sx={{ bgcolor: 'transparent', boxShadow: 'none', borderRadius: 0 }}>
            <Table>
              <TableHead sx={{ bgcolor: 'rgba(0,0,0,0.02)' }}>
                <TableRow>
                  <TableCell sx={{ fontWeight: 500 }}>Workflow Details</TableCell>
                  <TableCell sx={{ fontWeight: 500 }}>Workflow Key</TableCell>
                  <TableCell sx={{ fontWeight: 500 }}>Active Version</TableCell>
                  <TableCell sx={{ fontWeight: 500 }}>Versions</TableCell>
                  <TableCell sx={{ fontWeight: 500 }}>Updated At</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 500 }}>Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {filteredWorkflows.length === 0 ? (<TableRow>
                    <TableCell colSpan={6} align="center" sx={{ py: 6 }}>
                      <Typography color="text.secondary">No workflows found. Register a workflow to get started.</Typography>
                    </TableCell>
                  </TableRow>) : (filteredWorkflows.map((row) => (<TableRow key={row.id} hover sx={{ '&:hover': { bgcolor: 'rgba(0,0,0,0.01) !important' } }}>
                      <TableCell>
                        <Typography variant="body2" sx={{ fontWeight: 500, color: 'text.primary' }}>
                          {row.name}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {row.description || 'No description provided'}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Chip label={row.key} size="small" sx={{ fontFamily: 'monospace', fontWeight: 400, bgcolor: 'action.selected', border: '1px solid', borderColor: 'divider' }}/>
                      </TableCell>
                      <TableCell>
                        {row.activeVersion ? (<Chip label={`v${row.activeVersion}`} color="success" size="small" sx={{ fontWeight: 500 }}/>) : (<Chip label="None" size="small" sx={{ fontStyle: 'italic', bgcolor: 'action.disabledBackground' }}/>)}
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
                          {row.activeVersion && (<Tooltip title="Run Workflow">
                              <IconButton onClick={() => handleRunWorkflow(row)} size="small" sx={{ border: '1px solid rgba(16,185,129,0.2)', bgcolor: 'rgba(16,185,129,0.05)', color: '#10b981' }}>
                                <Play size={16}/>
                              </IconButton>
                            </Tooltip>)}
                          <Tooltip title="Manage Versions">
                            <IconButton onClick={() => onOpenVersions(row)} color="primary" size="small" sx={{ border: '1px solid', borderColor: 'divider', bgcolor: 'action.hover' }}>
                              <History size={16}/>
                            </IconButton>
                          </Tooltip>
                          <Tooltip title="Copy/Clone Workflow">
                            <IconButton onClick={() => onOpenCopy(row)} color="secondary" size="small" sx={{ border: '1px solid', borderColor: 'divider', bgcolor: 'action.hover' }}>
                              <Copy size={16}/>
                            </IconButton>
                          </Tooltip>
                          <Tooltip title="Delete Workflow">
                            <IconButton onClick={() => onDeleteWorkflow(row.id)} color="error" size="small" sx={{ border: '1px solid', borderColor: 'divider', bgcolor: 'action.hover' }}>
                              <Trash2 size={16}/>
                            </IconButton>
                          </Tooltip>
                        </Box>
                      </TableCell>
                    </TableRow>)))}
              </TableBody>
            </Table>
          </TableContainer>
        </Card>

        {/* Execution History Drawer */}
        <Drawer anchor="right" open={historyOpen} onClose={() => setHistoryOpen(false)} slotProps={{ paper: { sx: { width: 780, bgcolor: 'background.paper', borderLeft: '1px solid', borderColor: 'divider' } } }}>
          <ExecutionHistory onClose={() => setHistoryOpen(false)} onShowNotification={onShowNotification}/>
        </Drawer>
      </Container>
    </Box>);
};
