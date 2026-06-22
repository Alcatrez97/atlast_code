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
import TimerIcon from '@mui/icons-material/Timer';
import GroupIcon from '@mui/icons-material/Group';
import { useWorkflowStore } from '../store/workflowStore';
import type { Bucket } from '../store/workflowStore';
import { BucketFormDrawer } from './BucketFormDrawer';

interface BucketRegistryPageProps {
  onShowNotification: (message: string, severity: 'success' | 'error') => void;
}

export const BucketRegistryPage: React.FC<BucketRegistryPageProps> = ({ onShowNotification }) => {
  const { goBack } = useWorkflowStore();
  const [buckets, setBuckets] = useState<Bucket[]>([]);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [selectedBucket, setSelectedBucket] = useState<Bucket | null>(null);

  const fetchBuckets = async () => {
    setLoading(true);
    try {
      const res = await fetch('/api/buckets');
      if (!res.ok) throw new Error('Failed to fetch buckets');
      const data = await res.json();
      setBuckets(data);
    } catch (err: any) {
      onShowNotification(err.message, 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchBuckets();
  }, []);

  const handleDelete = async (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!window.confirm('Are you sure you want to delete this bucket entry? This might break workflows referencing this bucketId.')) return;
    try {
      const res = await fetch(`/api/buckets/${id}`, { method: 'DELETE' });
      if (!res.ok) throw new Error('Failed to delete bucket');
      onShowNotification('Bucket deleted from registry', 'success');
      await fetchBuckets();
    } catch (err: any) {
      onShowNotification(err.message, 'error');
    }
  };

  const handleEdit = (bucket: Bucket) => {
    setSelectedBucket(bucket);
    setDrawerOpen(true);
  };

  const handleCreateNew = () => {
    setSelectedBucket(null);
    setDrawerOpen(true);
  };

  const getPriorityColor = (p: string) => {
    switch (p?.toUpperCase()) {
      case 'CRITICAL': return { bg: 'rgba(239, 68, 68, 0.15)', text: '#ef4444', border: 'rgba(239, 68, 68, 0.3)' };
      case 'HIGH': return { bg: 'rgba(249, 115, 22, 0.15)', text: '#f97316', border: 'rgba(249, 115, 22, 0.3)' };
      case 'MEDIUM': return { bg: 'rgba(234, 179, 8, 0.15)', text: '#eab308', border: 'rgba(234, 179, 8, 0.3)' };
      case 'LOW': return { bg: 'rgba(34, 197, 94, 0.15)', text: '#22c55e', border: 'rgba(34, 197, 94, 0.3)' };
      default: return { bg: 'rgba(148, 163, 184, 0.15)', text: '#94a3b8', border: 'rgba(148, 163, 184, 0.3)' };
    }
  };

  const filteredBuckets = buckets.filter(b =>
    b.name.toLowerCase().includes(search.toLowerCase()) ||
    b.bucketId.toLowerCase().includes(search.toLowerCase()) ||
    (b.category && b.category.toLowerCase().includes(search.toLowerCase())) ||
    (b.ownerGroup && b.ownerGroup.toLowerCase().includes(search.toLowerCase()))
  );

  return (
    <Box sx={{ bgcolor: 'background.default', minHeight: '92vh', py: 4, color: 'text.primary', transition: 'background-color 0.25s ease-in-out' }}>
      <Container maxWidth="lg">
        {/* Title bar */}
        <Box sx={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', mb: 4, gap: 2 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <IconButton onClick={() => goBack()} sx={{ color: '#a855f7', border: '1px solid rgba(168,85,247,0.2)' }}>
              <ArrowBackIcon />
            </IconButton>
            <Box>
              <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: '-0.5px' }}>
                Outcome Bucket Registry
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Configure operational endpoints, SLAs, and assignment owners for business outcomes.
              </Typography>
            </Box>
          </Box>
          <Button
            variant="contained" startIcon={<AddIcon />}
            onClick={handleCreateNew}
            sx={{
              background: 'linear-gradient(135deg, #a855f7 0%, #7e22ce 100%)',
              boxShadow: '0 4px 14px rgba(168,85,247,0.3)',
              fontWeight: 700,
              '&:hover': { background: '#7e22ce' }
            }}
          >
            Register Bucket
          </Button>
        </Box>

        {/* Search bar */}
        <Paper sx={{
          p: '4px 12px', display: 'flex', alignItems: 'center', mb: 4,
          bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider', borderRadius: 2
        }}>
          <SearchIcon sx={{ color: 'text.secondary', mr: 1 }} />
          <InputBase
            placeholder="Search buckets by ID, Name, Owner, or Category..."
            value={search} onChange={(e) => setSearch(e.target.value)}
            sx={{ ml: 1, flex: 1, color: 'text.primary', fontSize: '14px' }}
          />
        </Paper>

        {/* Loading / Content */}
        {loading && buckets.length === 0 ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 12 }}>
            <CircularProgress color="secondary" />
          </Box>
        ) : filteredBuckets.length === 0 ? (
          <Box sx={{
            p: 8, border: '1px dashed', borderColor: 'divider', borderRadius: 2,
            textAlign: 'center', bgcolor: 'background.paper'
          }}>
            <Typography variant="body1" color="text.secondary">
              No outcome buckets found. Click "Register Bucket" to create one.
            </Typography>
          </Box>
        ) : (
          <Grid container spacing={3}>
            {filteredBuckets.map((b) => {
              const colors = getPriorityColor(b.priority);
              return (
                <Grid size={{ xs: 12, sm: 6, md: 4 }} key={b.id}>
                  <Card sx={{
                    height: '100%', display: 'flex', flexDirection: 'column',
                    bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider',
                    transition: 'transform 0.2s, border-color 0.2s',
                    '&:hover': {
                      transform: 'translateY(-2px)',
                      borderColor: colors.border
                    }
                  }}>
                    <CardContent sx={{ flexGrow: 1, p: 2.5 }}>
                      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 2 }}>
                        <Box>
                          <Typography variant="caption" sx={{
                            fontFamily: 'monospace', color: '#a855f7', fontWeight: 800,
                            letterSpacing: '1px', display: 'block', mb: 0.5
                          }}>
                            {b.bucketId}
                          </Typography>
                          <Typography variant="h6" sx={{ fontWeight: 700, lineHeight: 1.2 }}>
                            {b.name}
                          </Typography>
                        </Box>
                        {/* Priority Badge */}
                        <Chip
                          label={b.priority} size="small"
                          sx={{
                            height: 20, fontSize: '9px', fontWeight: 800,
                            bgcolor: colors.bg, color: colors.text,
                            border: `1px solid ${colors.border}`
                          }}
                        />
                      </Box>

                      <Typography variant="body2" color="text.secondary" sx={{
                        minHeight: 40, mb: 2, display: '-webkit-box',
                        WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden'
                      }}>
                        {b.description || 'No description provided.'}
                      </Typography>

                      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, mb: 2 }}>
                        {b.category && (
                          <Chip label={b.category} size="small" variant="outlined" sx={{ height: 18, fontSize: '8px', borderColor: 'rgba(255,255,255,0.08)' }} />
                        )}
                        {!b.active && (
                          <Chip label="SUSPENDED" size="small" color="error" sx={{ height: 18, fontSize: '8px', fontWeight: 700 }} />
                        )}
                      </Box>

                      <Divider sx={{ my: 1.5, borderColor: 'rgba(255,255,255,0.06)' }} />

                      <Grid container spacing={1}>
                        <Grid size={{ xs: 6 }} sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                          <TimerIcon sx={{ color: 'text.secondary', fontSize: 16 }} />
                          <Typography variant="caption" color="text.secondary">
                            SLA: <b>{b.slaHours ? `${b.slaHours}h` : 'N/A'}</b>
                          </Typography>
                        </Grid>
                        <Grid size={{ xs: 6 }} sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                          <GroupIcon sx={{ color: 'text.secondary', fontSize: 16 }} />
                          <Typography variant="caption" color="text.secondary" noWrap>
                            Owner: <b>{b.ownerGroup || 'None'}</b>
                          </Typography>
                        </Grid>
                      </Grid>
                    </CardContent>

                    <CardActions sx={{ justifyContent: 'flex-end', px: 2, pb: 2, pt: 0 }}>
                      <IconButton size="small" onClick={() => handleEdit(b)} sx={{ color: 'text.secondary', '&:hover': { color: '#a855f7' } }}>
                        <EditIcon sx={{ fontSize: 18 }} />
                      </IconButton>
                      <IconButton size="small" color="error" onClick={(e) => handleDelete(b.id, e)}>
                        <DeleteIcon sx={{ fontSize: 18 }} />
                      </IconButton>
                    </CardActions>
                  </Card>
                </Grid>
              );
            })}
          </Grid>
        )}

        <BucketFormDrawer
          open={drawerOpen}
          bucket={selectedBucket}
          onClose={() => setDrawerOpen(false)}
          onRefresh={fetchBuckets}
          onShowNotification={onShowNotification}
        />
      </Container>
    </Box>
  );
};
