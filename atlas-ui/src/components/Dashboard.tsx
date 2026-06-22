import React, { useState, useEffect } from 'react';
import { Box, Typography, Grid, Card, CardContent, Paper, Button } from '@mui/material';
import AssignmentIcon from '@mui/icons-material/Assignment';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import PendingActionsIcon from '@mui/icons-material/PendingActions';
import DraftsIcon from '@mui/icons-material/Drafts';
import RuleIcon from '@mui/icons-material/Rule';
import { useWorkflowStore } from '../store/workflowStore';

export const Dashboard: React.FC = () => {
  const { workflows, setView } = useWorkflowStore();
  const [registeredBuckets, setRegisteredBuckets] = useState<any[]>([]);
  const [registeredRules, setRegisteredRules] = useState<any[]>([]);
  const [workloadSummary, setWorkloadSummary] = useState<{ totalPending: number; slaBreached: number }>({ totalPending: 0, slaBreached: 0 });

  useEffect(() => {
    fetch('/api/buckets')
      .then(res => res.ok ? res.json() : [])
      .then(data => setRegisteredBuckets(data))
      .catch(() => setRegisteredBuckets([]));

    fetch('/api/rules')
      .then(res => res.ok ? res.json() : [])
      .then(data => setRegisteredRules(data))
      .catch(() => setRegisteredRules([]));

    fetch('/api/bucket-executions/workload')
      .then(res => res.ok ? res.json() : [])
      .then((data: any[]) => {
        const totalPending = data.reduce((s, w) => s + (w.pending || 0), 0);
        const slaBreached = data.reduce((s, w) => s + (w.slaBreached || 0), 0);
        setWorkloadSummary({ totalPending, slaBreached });
      })
      .catch(() => { });
  }, []);

  // Calculate Metrics
  const totalWorkflows = workflows.length;
  const activePipelines = workflows.filter(w => w.activeVersion != null).length;

  let pendingReviews = 0;
  let totalDrafts = 0;
  workflows.forEach(w => {
    if (w.versions) {
      w.versions.forEach(v => {
        if (v.status.toUpperCase() === 'REVIEW') pendingReviews++;
        if (v.status.toUpperCase() === 'DRAFT') totalDrafts++;
      });
    }
  });

  return (
    <Box sx={{ p: 4, display: 'flex', flexDirection: 'column', gap: 4, bgcolor: 'background.default', minHeight: '92vh', transition: 'background-color 0.25s ease-in-out' }}>
      {/* Page Header */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 800, color: 'text.primary' }}>
            System Dashboard
          </Typography>
          <Typography variant="body1" color="text.secondary">
            Operational overview of pipeline definition health, registries, and workload queues.
          </Typography>
        </Box>
      </Box>

      {/* Metrics Row */}
      <Grid container spacing={3}>
        <Grid size={{ xs: 12, sm: 6, md: 2.4 }}>
          <Card sx={{ bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider' }}>
            <CardContent sx={{ display: 'flex', gap: 2, alignItems: 'center', p: 2.5 }}>
              <Box sx={{
                bgcolor: 'rgba(99, 102, 241, 0.1)',
                color: 'primary.main',
                borderRadius: 3,
                width: 48,
                height: 48,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}>
                <AssignmentIcon />
              </Box>
              <Box>
                <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 600 }}>Total Workflows</Typography>
                <Typography variant="h4" sx={{ fontWeight: 800 }}>{totalWorkflows}</Typography>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, sm: 6, md: 2.4 }}>
          <Card sx={{ bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider' }}>
            <CardContent sx={{ display: 'flex', gap: 2, alignItems: 'center', p: 2.5 }}>
              <Box sx={{
                bgcolor: 'rgba(20, 184, 166, 0.1)',
                color: 'secondary.main',
                borderRadius: 3,
                width: 48,
                height: 48,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}>
                <CheckCircleIcon />
              </Box>
              <Box>
                <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 600 }}>Active Published</Typography>
                <Typography variant="h4" sx={{ fontWeight: 800 }}>{activePipelines}</Typography>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, sm: 6, md: 2.4 }}>
          <Card sx={{ bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider' }}>
            <CardContent sx={{ display: 'flex', gap: 2, alignItems: 'center', p: 2.5 }}>
              <Box sx={{
                bgcolor: 'rgba(245, 158, 11, 0.1)',
                color: 'warning.main',
                borderRadius: 3,
                width: 48,
                height: 48,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}>
                <PendingActionsIcon />
              </Box>
              <Box>
                <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 600 }}>Pending Reviews</Typography>
                <Typography variant="h4" sx={{ fontWeight: 800 }}>{pendingReviews}</Typography>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, sm: 6, md: 2.4 }}>
          <Card sx={{ bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider' }}>
            <CardContent sx={{ display: 'flex', gap: 2, alignItems: 'center', p: 2.5 }}>
              <Box sx={{
                bgcolor: 'rgba(139, 92, 246, 0.1)',
                color: '#a78bfa',
                borderRadius: 3,
                width: 48,
                height: 48,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}>
                <RuleIcon />
              </Box>
              <Box>
                <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 600 }}>Rule Registry</Typography>
                <Typography variant="h4" sx={{ fontWeight: 800 }}>{registeredRules.length}</Typography>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, sm: 6, md: 2.4 }}>
          <Card sx={{ bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider' }}>
            <CardContent sx={{ display: 'flex', gap: 2, alignItems: 'center', p: 2.5 }}>
              <Box sx={{
                bgcolor: 'rgba(156, 163, 175, 0.1)',
                color: '#9ca3af',
                borderRadius: 3,
                width: 48,
                height: 48,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}>
                <DraftsIcon />
              </Box>
              <Box>
                <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 600 }}>Draft Configs</Typography>
                <Typography variant="h4" sx={{ fontWeight: 800 }}>{totalDrafts}</Typography>
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Bucket Overview Mini-Widget */}
      <Box>
        <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', letterSpacing: '1px', display: 'block', mb: 1.5 }}>
          OUTCOME BUCKET REGISTRY OVERVIEW
        </Typography>
        <Grid container spacing={2}>
          {[
            { label: 'CRITICAL PRIORITY', count: registeredBuckets.filter(b => b.priority?.toUpperCase() === 'CRITICAL').length, color: '#ef4444', bg: 'rgba(239, 68, 68, 0.08)' },
            { label: 'HIGH PRIORITY', count: registeredBuckets.filter(b => b.priority?.toUpperCase() === 'HIGH').length, color: '#f97316', bg: 'rgba(249, 115, 22, 0.08)' },
            { label: 'MEDIUM PRIORITY', count: registeredBuckets.filter(b => b.priority?.toUpperCase() === 'MEDIUM').length, color: '#eab308', bg: 'rgba(234, 179, 8, 0.08)' },
            { label: 'LOW PRIORITY', count: registeredBuckets.filter(b => b.priority?.toUpperCase() === 'LOW').length, color: '#22c55e', bg: 'rgba(34, 197, 94, 0.08)' },
          ].map((item, idx) => (
            <Grid size={{ xs: 6, sm: 3 }} key={idx}>
              <Paper sx={{
                p: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider', borderRadius: 2
              }}>
                <Box>
                  <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 700, fontSize: '10px' }}>
                    {item.label}
                  </Typography>
                  <Typography variant="h5" sx={{ fontWeight: 800, color: item.color, mt: 0.5 }}>
                    {item.count}
                  </Typography>
                </Box>
                <Box sx={{
                  width: 32, height: 32, borderRadius: '50%',
                  bgcolor: item.bg, border: `1px solid ${item.color}30`,
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  color: item.color, fontWeight: 800, fontSize: '12px'
                }}>
                  {item.label.charAt(0)}
                </Box>
              </Paper>
            </Grid>
          ))}
        </Grid>
      </Box>

      {/* Bucket Workload Summary Widget */}
      {(workloadSummary.totalPending > 0 || workloadSummary.slaBreached > 0) && (
        <Box sx={{
          p: 2.5,
          borderRadius: 2,
          background: workloadSummary.slaBreached > 0
            ? 'linear-gradient(135deg, rgba(248,113,113,0.08), rgba(239,68,68,0.04))'
            : 'linear-gradient(135deg, rgba(251,191,36,0.08), rgba(245,158,11,0.04))',
          border: workloadSummary.slaBreached > 0
            ? '1px solid rgba(248,113,113,0.3)'
            : '1px solid rgba(251,191,36,0.25)',
          display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2
        }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <Box sx={{ fontSize: '1.5rem' }}>{workloadSummary.slaBreached > 0 ? '🚨' : '⏳'}</Box>
            <Box>
              <Typography sx={{ color: workloadSummary.slaBreached > 0 ? '#f87171' : '#fbbf24', fontWeight: 700, fontSize: '0.9rem' }}>
                {workloadSummary.slaBreached > 0
                  ? `${workloadSummary.slaBreached} SLA breach${workloadSummary.slaBreached > 1 ? 'es' : ''} detected`
                  : `${workloadSummary.totalPending} execution${workloadSummary.totalPending > 1 ? 's' : ''} pending bucket review`}
              </Typography>
              <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                {workloadSummary.slaBreached > 0
                  ? `${workloadSummary.totalPending} total pending, ${workloadSummary.slaBreached} have exceeded SLA — immediate action required`
                  : 'Open Bucket Workload to review and resolve pending items'}
              </Typography>
            </Box>
          </Box>
          <Button
            size="small"
            variant="outlined"
            onClick={() => setView('bucketWorkload')}
            sx={{
              borderColor: workloadSummary.slaBreached > 0 ? 'rgba(248,113,113,0.5)' : 'rgba(251,191,36,0.5)',
              color: workloadSummary.slaBreached > 0 ? '#f87171' : '#fbbf24',
              whiteSpace: 'nowrap',
              '&:hover': { background: workloadSummary.slaBreached > 0 ? 'rgba(248,113,113,0.08)' : 'rgba(251,191,36,0.08)' }
            }}
          >
            Open Workload →
          </Button>
        </Box>
      )}
    </Box>
  );
};
