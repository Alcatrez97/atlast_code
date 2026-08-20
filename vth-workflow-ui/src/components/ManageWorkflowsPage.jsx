import React, { useState } from 'react';
import { Box, Typography, Button, Card, TableContainer, Table, TableHead, TableBody, TableRow, TableCell, Paper, TextField, InputAdornment, Tooltip, IconButton, Chip, Drawer, Badge, Container, Select, MenuItem, FormControl } from '@mui/material';
import { Plus, Search, Trash2, History, Play, PlayCircle, Copy } from 'lucide-react';
import GlobeIcon from '@mui/icons-material/Public';
import CircleIcon from '@mui/icons-material/TripOrigin';
import { useWorkflowStore } from '../store/workflowStore.js';
import { ExecutionHistory } from './ExecutionHistory';

const CIRCLE_OPTIONS = [
  { id: 101, label: '101 - MH (Maharashtra)', badge: 'MH', color: '#00A05A' },
  { id: 102, label: '102 - DL (Delhi)', badge: 'DL', color: '#0CADEF' },
  { id: 103, label: '103 - KA (Karnataka)', badge: 'KA', color: '#8b5cf6' },
  { id: 104, label: '104 - TN (Tamil Nadu)', badge: 'TN', color: '#ff6d00' },
  { id: 105, label: '105 - DL_NCR (Delhi NCR)', badge: 'NCR', color: '#d97706' },
  { id: 106, label: '106 - ROWB (West Bengal)', badge: 'WB', color: '#64748b' },
];

export const ManageWorkflowsPage = ({ onOpenCreate, onOpenVersions, onDeleteWorkflow, onShowNotification, onOpenCopy }) => {
    const { workflows, setView, setSelectedWorkflow, executions } = useWorkflowStore();
    const [searchTerm, setSearchTerm] = useState('');
    const [circleFilter, setCircleFilter] = useState('ALL');
    const [historyOpen, setHistoryOpen] = useState(false);

    const handleRunWorkflow = (workflow) => {
        setSelectedWorkflow(workflow);
        setView('executor');
    };

    const filteredWorkflows = workflows.filter(w => {
        const matchesSearch = w.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
            w.key.toLowerCase().includes(searchTerm.toLowerCase()) ||
            w.description?.toLowerCase().includes(searchTerm.toLowerCase());

        let matchesCircle = true;
        if (circleFilter !== 'ALL') {
            if (w.circleId == null) {
                matchesCircle = true; // Global workflows match all filters
            } else {
                matchesCircle = Number(w.circleId) === Number(circleFilter);
            }
        }
        return matchesSearch && matchesCircle;
    });

    const renderCircleScope = (circleId) => {
        if (circleId == null) {
            return (
                <Chip
                    icon={<GlobeIcon sx={{ fontSize: '14px !important', color: '#3b82f6 !important' }} />}
                    label="All Circles"
                    size="small"
                    variant="outlined"
                    sx={{ height: 22, fontSize: '11px', fontWeight: 600, color: '#3b82f6', borderColor: 'rgba(59,130,246,0.3)', bgcolor: 'rgba(59,130,246,0.05)' }}
                />
            );
        }
        const opt = CIRCLE_OPTIONS.find(o => o.id === Number(circleId)) || { badge: String(circleId), label: `Circle ${circleId}`, color: '#6366f1' };
        return (
            <Chip
                label={opt.label || `Circle ${circleId}`}
                size="small"
                sx={{
                    height: 22,
                    fontSize: '11px',
                    fontWeight: 700,
                    bgcolor: `${opt.color}15`,
                    color: opt.color,
                    border: `1px solid ${opt.color}40`
                }}
            />
        );
    };

    return (<Box sx={{ minHeight: '92vh', py: 4, transition: 'background-color 0.25s ease-in-out' }}>
      <Container maxWidth={false} sx={{ px: { xs: 2, sm: 3, md: 4 } }}>
        {/* Page Header */}
        <Box sx={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', mb: 4, gap: 2 }}>
          <Box>
            <Typography variant="h5" sx={{ fontWeight: 500, color: 'text.primary', mb: 0.5 }}>
              Workflow Definitions
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Manage, version, and govern enterprise decision structures across operational circles.
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
          <Box sx={{ p: 2.5, display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid', borderColor: 'divider', bgcolor: 'rgba(255,255,255,0.01)', gap: 2 }}>
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

            {/* Circle Filter Dropdown */}
            <FormControl size="small" sx={{ minWidth: 200 }}>
              <Select
                value={circleFilter}
                onChange={(e) => setCircleFilter(e.target.value)}
                displayEmpty
                sx={{ borderRadius: 2, fontSize: '13px', fontWeight: 500 }}
              >
                <MenuItem value="ALL" sx={{ fontSize: '13px', fontWeight: 500 }}>
                  <GlobeIcon sx={{ fontSize: 16, mr: 1, color: '#3b82f6' }} /> All Circle Scope
                </MenuItem>
                {CIRCLE_OPTIONS.map((opt) => (
                  <MenuItem key={opt.id} value={opt.id} sx={{ fontSize: '13px', fontWeight: 500 }}>
                    <CircleIcon sx={{ fontSize: 14, mr: 1, color: opt.color }} /> Filter by {opt.label}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Box>

          <TableContainer component={Paper} sx={{ bgcolor: 'transparent', boxShadow: 'none', borderRadius: 0 }}>
            <Table>
              <TableHead sx={{ bgcolor: 'rgba(0,0,0,0.02)' }}>
                <TableRow>
                  <TableCell sx={{ fontWeight: 500 }}>Workflow Details</TableCell>
                  <TableCell sx={{ fontWeight: 500 }}>Workflow Key</TableCell>
                  <TableCell sx={{ fontWeight: 500 }}>Circle Scope</TableCell>
                  <TableCell sx={{ fontWeight: 500 }}>Active Version</TableCell>
                  <TableCell sx={{ fontWeight: 500 }}>Versions</TableCell>
                  <TableCell sx={{ fontWeight: 500 }}>Updated At</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 500 }}>Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {filteredWorkflows.length === 0 ? (<TableRow>
                    <TableCell colSpan={7} align="center" sx={{ py: 6 }}>
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
                        {renderCircleScope(row.circleId)}
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
