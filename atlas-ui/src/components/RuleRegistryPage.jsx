import React, { useEffect, useState } from 'react';
import { Box, Container, Grid, Paper, Typography, Button, Card, CardContent, CardActions, Chip, IconButton, InputBase, CircularProgress, Divider } from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import SearchIcon from '@mui/icons-material/Search';
import CodeIcon from '@mui/icons-material/Code';
import ToggleOnIcon from '@mui/icons-material/ToggleOn';
import ToggleOffIcon from '@mui/icons-material/ToggleOff';
import { useWorkflowStore } from '../store/workflowStore.js';
import { RuleForm } from './RuleForm';
export const RuleRegistryPage = ({ onShowNotification }) => {
  const { goBack, setView, showConfirm } = useWorkflowStore();
  const [rules, setRules] = useState([]);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');
  const [viewMode, setViewMode] = useState('list');
  const [selectedRule, setSelectedRule] = useState(null);
  const fetchRules = async () => {
    setLoading(true);
    try {
      const res = await fetch('/api/rules');
      if (!res.ok)
        throw new Error('Failed to fetch rules');
      const data = await res.json();
      setRules(data);
    }
    catch (err) {
      onShowNotification(err.message, 'error');
    }
    finally {
      setLoading(false);
    }
  };
  useEffect(() => {
    fetchRules();
  }, []);
  const handleDelete = async (id, e) => {
    e.stopPropagation();
    const isConfirmed = await showConfirm({
      title: 'Delete Rule Entry',
      message: 'Are you sure you want to delete this rule entry? Workflows referencing this ruleKey may fail.',
      confirmText: 'Delete Rule',
      cancelText: 'Cancel',
      severity: 'error'
    });
    if (!isConfirmed)
      return;
    try {
      const res = await fetch(`/api/rules/${id}`, { method: 'DELETE' });
      if (!res.ok)
        throw new Error('Failed to delete rule');
      onShowNotification('Rule deleted from registry', 'success');
      await fetchRules();
    }
    catch (err) {
      onShowNotification(err.message, 'error');
    }
  };
  const handleEdit = (rule) => {
    setSelectedRule(rule);
    setViewMode('edit');
  };
  const handleCreateNew = () => {
    setSelectedRule(null);
    setViewMode('create');
  };
  const filteredRules = rules.filter(r => r.name.toLowerCase().includes(search.toLowerCase()) ||
    r.ruleKey.toLowerCase().includes(search.toLowerCase()) ||
    (r.description && r.description.toLowerCase().includes(search.toLowerCase())) ||
    r.expression.toLowerCase().includes(search.toLowerCase()));
  const accentColor = '#8b5cf6'; // Indigo/violet accent
  return (<Box sx={{ bgcolor: 'background.default', minHeight: '92vh', py: 4, color: 'text.primary', transition: 'background-color 0.25s ease-in-out' }}>
    <Container maxWidth={false} sx={{ px: { xs: 2, sm: 3, md: 4 } }}>
      {/* Title bar */}
      <Box sx={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', mb: 4, gap: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          <IconButton onClick={() => goBack()} sx={{ color: '#2F3043', border: '1px solid #2f304344' }}>
            <ArrowBackIcon />
          </IconButton>
          <Box>
            <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: '-0.5px' }}>
              Expression Rule Registry
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Configure reusable, pre-validated SpEL rules for routing decision logic.
            </Typography>
          </Box>
        </Box>
        <Box sx={{ display: 'flex', gap: 2 }}>
          <Button variant="outlined" onClick={() => setView('ruleHelp')} sx={{
            borderColor: '#686b9744',
            color: '#2F3043',
            fontWeight: 700,
            '&:hover': {
              borderColor: '#686b9744',
              bgcolor: 'rgba(104, 107, 151, 0.12)',
            }
          }}>
            SpEL Reference Guide
          </Button>
          <Button variant="contained" startIcon={<AddIcon />} onClick={handleCreateNew} sx={{
            background: 'linear-gradient(135deg, #686b97ff 0%, #2F3043 100%)',
            boxShadow: '0 1px 1px #686b97ff',
            fontWeight: 700,
            '&:hover': { background: 'linear-gradient(135deg,  #686b97ff 100%)' }
          }}>
            Register Rule
          </Button>
        </Box>
      </Box>

      {viewMode !== 'list' ? (<RuleForm rule={selectedRule} onClose={() => setViewMode('list')} onRefresh={fetchRules} onShowNotification={onShowNotification} />) : (<>
        {/* Search bar */}
        <Paper sx={{
          p: '4px 12px', display: 'flex', alignItems: 'center', mb: 4,
          bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider', borderRadius: 2
        }}>
          <SearchIcon sx={{ color: 'text.secondary', mr: 1 }} />
          <InputBase placeholder="Search rules by Key, Name, Description, or Expression..." value={search} onChange={(e) => setSearch(e.target.value)} sx={{ ml: 1, flex: 1, color: 'text.primary', fontSize: '14px' }} />
        </Paper>

        {/* Loading / Content */}
        {loading && rules.length === 0 ? (<Box sx={{ display: 'flex', justifyContent: 'center', py: 12 }}>
          <CircularProgress color="secondary" />
        </Box>) : filteredRules.length === 0 ? (<Box sx={{
          p: 8, border: '1px dashed', borderColor: 'divider', borderRadius: 2,
          textAlign: 'center', bgcolor: 'background.paper'
        }}>
          <Typography variant="body1" color="text.secondary">
            No rules found. Click "Register Rule" to create one.
          </Typography>
        </Box>) : (<Grid container spacing={3}>
          {filteredRules.map((r) => {
            return (<Grid size={{ xs: 12, sm: 6, md: 4 }} key={r.id}>
              <Card sx={{
                height: '100%', display: 'flex', flexDirection: 'column',
                bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider',
                transition: 'transform 0.2s, border-color 0.2s',
                '&:hover': {
                  transform: 'translateY(-2px)',
                  borderColor: accentColor
                }
              }}>
                <CardContent sx={{ flexGrow: 1, p: 2.5 }}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 1.5 }}>
                    <Box sx={{ overflow: 'hidden', mr: 1 }}>
                      <Typography variant="caption" sx={{
                        fontFamily: 'monospace', color: accentColor, fontWeight: 800,
                        letterSpacing: '1px', display: 'block', mb: 0.5, textOverflow: 'ellipsis', overflow: 'hidden'
                      }}>
                        {r.ruleKey}
                      </Typography>
                      <Typography variant="h6" sx={{ fontWeight: 700, lineHeight: 1.2, textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>
                        {r.name}
                      </Typography>
                    </Box>
                    {/* Status Chip */}
                    <Chip label={r.active ? "ACTIVE" : "INACTIVE"} size="small" sx={{
                      height: 18, fontSize: '8px', fontWeight: 800,
                      bgcolor: r.active ? 'rgba(34, 197, 94, 0.12)' : 'rgba(239, 68, 68, 0.12)',
                      color: r.active ? '#22c55e' : '#ef4444',
                      border: r.active ? '1px solid rgba(34, 197, 94, 0.2)' : '1px solid rgba(239, 68, 68, 0.2)'
                    }} />
                  </Box>

                  <Typography variant="body2" color="text.secondary" sx={{
                    minHeight: 40, mb: 2.5, display: '-webkit-box',
                    WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden'
                  }}>
                    {r.description || 'No description provided.'}
                  </Typography>

                  <Divider sx={{ my: 1.5, borderColor: 'rgba(44, 44, 44, 0.3)' }} />

                  {/* Expression Preview */}
                  <Box sx={{ bgcolor: 'rgba(196, 196, 196, 0.2)', p: 1.5, borderRadius: 1.5, border: '1px solid rgba(255,255,255,0.04)' }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.8, mb: 0.8 }}>
                      <CodeIcon sx={{ fontSize: 13, color: 'text.secondary' }} />
                      <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', letterSpacing: '0.5px' }}>
                        SPEL EXPRESSION
                      </Typography>
                    </Box>
                    <Typography variant="body2" sx={{
                      fontFamily: 'monospace', color: '#a78bfa',
                      whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
                      fontSize: '12px'
                    }}>
                      {r.expression}
                    </Typography>
                  </Box>

                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mt: 2 }}>
                    {r.active ? (<ToggleOnIcon sx={{ color: '#22c55e', fontSize: 16 }} />) : (<ToggleOffIcon sx={{ color: '#ef4444', fontSize: 16 }} />)}
                    <Typography variant="caption" color="text.secondary">
                      Status: <b>{r.active ? 'Evaluation Enabled' : 'Evaluation Suspended'}</b>
                    </Typography>
                  </Box>
                </CardContent>

                <CardActions sx={{ justifyContent: 'flex-end', px: 2, pb: 2, pt: 0 }}>
                  <IconButton size="small" onClick={() => handleEdit(r)} sx={{ color: 'text.secondary', '&:hover': { color: accentColor } }}>
                    <EditIcon sx={{ fontSize: 18 }} />
                  </IconButton>
                  <IconButton size="small" color="error" onClick={(e) => handleDelete(r.id, e)}>
                    <DeleteIcon sx={{ fontSize: 18 }} />
                  </IconButton>
                </CardActions>
              </Card>
            </Grid>);
          })}
        </Grid>)}
      </>)}
    </Container>
  </Box>);
};
