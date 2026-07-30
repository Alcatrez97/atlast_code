import React, { useState, useEffect } from 'react';
import { Box, Typography, Grid, Card, CardContent, Paper, Button } from '@mui/material';
import { FileText, CheckCircle, Clock, Edit3, Sliders } from 'lucide-react';
import { useWorkflowStore } from '../store/workflowStore.js';
export const Dashboard = () => {
    const { workflows, setView } = useWorkflowStore();
    const [registeredBuckets, setRegisteredBuckets] = useState([]);
    const [registeredRules, setRegisteredRules] = useState([]);
    const [workloadSummary, setWorkloadSummary] = useState({ totalPending: 0, slaBreached: 0 });
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
            .then((data) => {
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
                if (v.status.toUpperCase() === 'REVIEW')
                    pendingReviews++;
                if (v.status.toUpperCase() === 'DRAFT')
                    totalDrafts++;
            });
        }
    });
    return (<Box sx={{ p: 4, display: 'flex', flexDirection: 'column', gap: 4, minHeight: '92vh', transition: 'background-color 0.25s ease-in-out' }}>
      {/* Page Header */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Box>
          <Typography sx={{ fontWeight: 500, color: 'text.primary', fontSize: '1.25rem', mb: 0.5 }}>
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
            bgcolor: 'rgba(12, 173, 239, 0.1)',
            color: '#0CADEF', // Sky Blue
            borderRadius: 3,
            width: 48,
            height: 48,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center'
        }}>
                <FileText size={24}/>
              </Box>
              <Box>
                <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 500 }}>Total Workflows</Typography>
                <Typography sx={{ fontWeight: 600, fontSize: '1.75rem', lineHeight: 1.2 }}>{totalWorkflows}</Typography>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, sm: 6, md: 2.4 }}>
          <Card sx={{ bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider' }}>
            <CardContent sx={{ display: 'flex', gap: 2, alignItems: 'center', p: 2.5 }}>
              <Box sx={{
            bgcolor: 'rgba(0, 160, 90, 0.1)',
            color: '#00A05A', // Success Green
            borderRadius: 3,
            width: 48,
            height: 48,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center'
        }}>
                <CheckCircle size={24}/>
              </Box>
              <Box>
                <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 500 }}>Active Published</Typography>
                <Typography sx={{ fontWeight: 600, fontSize: '1.75rem', lineHeight: 1.2 }}>{activePipelines}</Typography>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, sm: 6, md: 2.4 }}>
          <Card sx={{ bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider' }}>
            <CardContent sx={{ display: 'flex', gap: 2, alignItems: 'center', p: 2.5 }}>
              <Box sx={{
            bgcolor: 'rgba(255, 198, 0, 0.1)',
            color: '#FFC600', // Mustard
            borderRadius: 3,
            width: 48,
            height: 48,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center'
        }}>
                <Clock size={24}/>
              </Box>
              <Box>
                <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 500 }}>Pending Reviews</Typography>
                <Typography sx={{ fontWeight: 600, fontSize: '1.75rem', lineHeight: 1.2 }}>{pendingReviews}</Typography>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, sm: 6, md: 2.4 }}>
          <Card sx={{ bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider' }}>
            <CardContent sx={{ display: 'flex', gap: 2, alignItems: 'center', p: 2.5 }}>
              <Box sx={{
            bgcolor: 'rgba(95, 0, 75, 0.1)',
            color: '#5F004B', // Purple
            borderRadius: 3,
            width: 48,
            height: 48,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center'
        }}>
                <Sliders size={24}/>
              </Box>
              <Box>
                <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 500 }}>Rule Registry</Typography>
                <Typography sx={{ fontWeight: 600, fontSize: '1.75rem', lineHeight: 1.2 }}>{registeredRules.length}</Typography>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, sm: 6, md: 2.4 }}>
          <Card sx={{ bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider' }}>
            <CardContent sx={{ display: 'flex', gap: 2, alignItems: 'center', p: 2.5 }}>
              <Box sx={{
            bgcolor: 'rgba(130, 131, 142, 0.1)',
            color: '#82838E', // Slate 60%
            borderRadius: 3,
            width: 48,
            height: 48,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center'
        }}>
                <Edit3 size={24}/>
              </Box>
              <Box>
                <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 500 }}>Draft Configs</Typography>
                <Typography sx={{ fontWeight: 600, fontSize: '1.75rem', lineHeight: 1.2 }}>{totalDrafts}</Typography>
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
        ].map((item, idx) => (<Grid size={{ xs: 6, sm: 3 }} key={idx}>
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
            </Grid>))}
        </Grid>
      </Box>

      {/* Bucket Workload Summary Widget */}
      {(workloadSummary.totalPending > 0 || workloadSummary.slaBreached > 0) && (<Box sx={{
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
          <Button size="small" variant="outlined" onClick={() => setView('bucketWorkload')} sx={{
                borderColor: workloadSummary.slaBreached > 0 ? 'rgba(248,113,113,0.5)' : 'rgba(251,191,36,0.5)',
                color: workloadSummary.slaBreached > 0 ? '#f87171' : '#fbbf24',
                whiteSpace: 'nowrap',
                '&:hover': { background: workloadSummary.slaBreached > 0 ? 'rgba(248,113,113,0.08)' : 'rgba(251,191,36,0.08)' }
            }}>
            Open Workload →
          </Button>
        </Box>)}
    </Box>);
};
