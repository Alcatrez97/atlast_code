import React, { useEffect, useState } from 'react';
import {
  Box, Container, Grid, Paper, Typography, Button, Card, CardContent,
  CardActions, Chip, IconButton, InputBase, CircularProgress, Divider
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import SearchIcon from '@mui/icons-material/Search';
import HttpIcon from '@mui/icons-material/Http';
import StorageIcon from '@mui/icons-material/Storage';
import TimerIcon from '@mui/icons-material/Timer';
import { useWorkflowStore } from '../store/workflowStore';
import type { Integration } from '../store/workflowStore';
import { IntegrationForm } from './IntegrationForm';

interface IntegrationRegistryPageProps {
  onShowNotification: (message: string, severity: 'success' | 'error') => void;
}

export const IntegrationRegistryPage: React.FC<IntegrationRegistryPageProps> = ({ onShowNotification }) => {
  const { goBack } = useWorkflowStore();
  const [integrations, setIntegrations] = useState<Integration[]>([]);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');
  const [viewMode, setViewMode] = useState<'list' | 'create' | 'edit'>('list');
  const [selectedIntegration, setSelectedIntegration] = useState<Integration | null>(null);

  const fetchIntegrations = async () => {
    setLoading(true);
    try {
      const res = await fetch('/api/integrations');
      if (!res.ok) throw new Error('Failed to fetch integrations');
      const data = await res.json();
      setIntegrations(data);
    } catch (err: any) {
      onShowNotification(err.message, 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchIntegrations();
  }, []);

  const handleDelete = async (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!window.confirm('Are you sure you want to delete this integration endpoint? Workflows referencing this provider will fail at runtime.')) return;
    try {
      const res = await fetch(`/api/integrations/${id}`, { method: 'DELETE' });
      if (!res.ok) throw new Error('Failed to delete integration');
      onShowNotification('Integration deleted from registry', 'success');
      await fetchIntegrations();
    } catch (err: any) {
      onShowNotification(err.message, 'error');
    }
  };

  const handleEdit = (integration: Integration) => {
    setSelectedIntegration(integration);
    setViewMode('edit');
  };

  const handleCreateNew = () => {
    setSelectedIntegration(null);
    setViewMode('create');
  };

  const filteredIntegrations = integrations.filter(i =>
    i.name.toLowerCase().includes(search.toLowerCase()) ||
    i.integrationKey.toLowerCase().includes(search.toLowerCase()) ||
    i.providerType.toLowerCase().includes(search.toLowerCase()) ||
    (i.endpointUrl && i.endpointUrl.toLowerCase().includes(search.toLowerCase()))
  );

  const accentColor = '#14b8a6'; // Teal/cyan accent

  return (
    <Box sx={{ bgcolor: 'background.default', minHeight: '92vh', py: 4, color: 'text.primary', transition: 'background-color 0.25s ease-in-out' }}>
      <Container maxWidth="lg">
        {/* Title bar */}
        <Box sx={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', mb: 4, gap: 2 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <IconButton onClick={() => goBack()} sx={{ color: accentColor, border: `1px solid rgba(20,184,166,0.2)` }}>
              <ArrowBackIcon />
            </IconButton>
            <Box>
              <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: '-0.5px' }}>
                Integration Registry
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Configure external REST APIs and Database datasources for context resolution.
              </Typography>
            </Box>
          </Box>
          <Button
            variant="contained" startIcon={<AddIcon />}
            onClick={handleCreateNew}
            sx={{
              background: `linear-gradient(135deg, ${accentColor} 0%, #0d9488 100%)`,
              boxShadow: `0 4px 14px rgba(20,184,166,0.3)`,
              fontWeight: 700,
              '&:hover': { background: '#0d9488' }
            }}
          >
            Register Integration
          </Button>
        </Box>

        {viewMode !== 'list' ? (
          <IntegrationForm
            integration={selectedIntegration}
            onClose={() => setViewMode('list')}
            onRefresh={fetchIntegrations}
            onShowNotification={onShowNotification}
          />
        ) : (
          <>
            {/* Search bar */}
            <Paper sx={{
              p: '4px 12px', display: 'flex', alignItems: 'center', mb: 4,
              bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider', borderRadius: 2
            }}>
              <SearchIcon sx={{ color: 'text.secondary', mr: 1 }} />
              <InputBase
                placeholder="Search integrations by Key, Name, Type, or Endpoint..."
                value={search} onChange={(e) => setSearch(e.target.value)}
                sx={{ ml: 1, flex: 1, color: 'text.primary', fontSize: '14px' }}
              />
            </Paper>

            {/* Loading / Content */}
            {loading && integrations.length === 0 ? (
              <Box sx={{ display: 'flex', justifyContent: 'center', py: 12 }}>
                <CircularProgress color="secondary" />
              </Box>
            ) : filteredIntegrations.length === 0 ? (
              <Box sx={{
                p: 8, border: '1px dashed', borderColor: 'divider', borderRadius: 2,
                textAlign: 'center', bgcolor: 'background.paper'
              }}>
                <Typography variant="body1" color="text.secondary">
                  No integration endpoints registered yet. Click "Register Integration" to configure one.
                </Typography>
              </Box>
            ) : (
              <Grid container spacing={3}>
                {filteredIntegrations.map((i) => {
                  const isRest = i.providerType === 'REST';
                  return (
                    <Grid size={{ xs: 12, sm: 6, md: 4 }} key={i.id}>
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
                                {i.integrationKey}
                              </Typography>
                              <Typography variant="h6" sx={{ fontWeight: 700, lineHeight: 1.2, textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>
                                {i.name}
                              </Typography>
                            </Box>
                            {/* Status Chip */}
                            <Chip
                              label={i.providerType}
                              size="small"
                              icon={isRest ? <HttpIcon style={{ fontSize: 14 }} /> : <StorageIcon style={{ fontSize: 12 }} />}
                              sx={{
                                height: 20, fontSize: '9px', fontWeight: 800,
                                bgcolor: isRest ? 'rgba(59, 130, 246, 0.12)' : 'rgba(245, 158, 11, 0.12)',
                                color: isRest ? '#3b82f6' : '#f59e0b',
                                border: isRest ? '1px solid rgba(59, 130, 246, 0.2)' : '1px solid rgba(245, 158, 11, 0.2)',
                                '& .MuiChip-icon': { color: 'inherit', mr: -0.2 }
                              }}
                            />
                          </Box>

                          {isRest ? (
                            <Box sx={{ minHeight: 65, mb: 1.5 }}>
                              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
                                REST ENDPOINT ({i.method})
                              </Typography>
                              <Typography variant="body2" sx={{
                                fontFamily: 'monospace', color: '#cbd5e1', fontSize: '11px',
                                wordBreak: 'break-all', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden'
                              }}>
                                {i.endpointUrl}
                              </Typography>
                            </Box>
                          ) : (
                            <Box sx={{ minHeight: 65, mb: 1.5 }}>
                              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
                                SQL QUERY
                              </Typography>
                              <Typography variant="body2" sx={{
                                fontFamily: 'monospace', color: '#cbd5e1', fontSize: '11px',
                                whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis'
                              }}>
                                {i.requestTemplate}
                              </Typography>
                            </Box>
                          )}

                          <Divider sx={{ my: 1.5, borderColor: 'rgba(255,255,255,0.06)' }} />

                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                              <TimerIcon sx={{ fontSize: 13, color: 'text.secondary' }} />
                              <Typography variant="caption" color="text.secondary">
                                Timeout: <b>{i.timeoutMs}ms</b>
                              </Typography>
                            </Box>
                          </Box>
                        </CardContent>

                        <CardActions sx={{ justifyContent: 'flex-end', px: 2, pb: 2, pt: 0 }}>
                          <IconButton size="small" onClick={() => handleEdit(i)} sx={{ color: 'text.secondary', '&:hover': { color: accentColor } }}>
                            <EditIcon sx={{ fontSize: 18 }} />
                          </IconButton>
                          <IconButton size="small" color="error" onClick={(e) => handleDelete(i.id!, e)}>
                            <DeleteIcon sx={{ fontSize: 18 }} />
                          </IconButton>
                        </CardActions>
                      </Card>
                    </Grid>
                  );
                })}
              </Grid>
            )}
          </>
        )}
      </Container>
    </Box>
  );
};
