import React from 'react';
import { Box, Typography, Card, CardContent, Divider } from '@mui/material';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import CancelIcon from '@mui/icons-material/Cancel';
import RuleIcon from '@mui/icons-material/Rule';
import HelpOutlineIcon from '@mui/icons-material/Help';
import ShoppingBagIcon from '@mui/icons-material/ShoppingBag';
import AccessAlarmIcon from '@mui/icons-material/AccessAlarm';
import CallSplitIcon from '@mui/icons-material/CallSplit';
import CallMergeIcon from '@mui/icons-material/CallMerge';
import AccountTreeIcon from '@mui/icons-material/AccountTree';
import TerminalIcon from '@mui/icons-material/Terminal';
import HourglassEmptyIcon from '@mui/icons-material/HourglassEmpty';

interface CatalogItem {
  type: string;
  label: string;
  description: string;
  icon: React.ReactNode;
  color: string;
}

const items: CatalogItem[] = [
  {
    type: 'START',
    label: 'Start Node',
    description: 'Entry point of the workflow execution.',
    icon: <PlayArrowIcon sx={{ color: '#10b981' }} />,
    color: '#10b981'
  },
  {
    type: 'END',
    label: 'End Node',
    description: 'Terminates workflow execution.',
    icon: <CancelIcon sx={{ color: '#ef4444' }} />,
    color: '#ef4444'
  },
  {
    type: 'RULE',
    label: 'Rule Eval',
    description: 'Evaluates dynamic conditional rule expressions.',
    icon: <RuleIcon sx={{ color: '#6366f1' }} />,
    color: '#6366f1'
  },
  {
    type: 'DECISION',
    label: 'Decision Branch',
    description: 'Decides routing edge based on variable evaluations.',
    icon: <HelpOutlineIcon sx={{ color: '#f59e0b' }} />,
    color: '#f59e0b'
  },
  {
    type: 'BUCKET',
    label: 'Bucket Task',
    description: 'Represents a core business action or SLA target.',
    icon: <ShoppingBagIcon sx={{ color: '#a855f7' }} />,
    color: '#a855f7'
  },
  {
    type: 'TIMER',
    label: 'Timer Delay',
    description: 'Stalls workflow execution for a relative period.',
    icon: <AccessAlarmIcon sx={{ color: '#14b8a6' }} />,
    color: '#14b8a6'
  },
  {
    type: 'PARALLEL',
    label: 'Parallel Split',
    description: 'Branches execution path in parallel pathways.',
    icon: <CallSplitIcon sx={{ color: '#f97316' }} />,
    color: '#f97316'
  },
  {
    type: 'JOIN',
    label: 'Join Merge',
    description: 'Waits and merges parallel execution pathways.',
    icon: <CallMergeIcon sx={{ color: '#f97316' }} />,
    color: '#f97316'
  },
  {
    type: 'SUB_WORKFLOW',
    label: 'Sub-Workflow',
    description: 'Executes a nested workflow instance as a sub-task.',
    icon: <AccountTreeIcon sx={{ color: '#3b82f6' }} />,
    color: '#3b82f6'
  },
  {
    type: 'COMMAND',
    label: 'Command Node',
    description: 'Emits a command payload to trigger external actions.',
    icon: <TerminalIcon sx={{ color: '#38bdf8' }} />,
    color: '#38bdf8'
  },
  {
    type: 'WAIT_EVENT',
    label: 'Wait Event Node',
    description: 'Pauses execution until matching event is received.',
    icon: <HourglassEmptyIcon sx={{ color: '#f59e0b' }} />,
    color: '#f59e0b'
  }
];

export const NodeCatalog: React.FC = () => {
  const onDragStart = (event: React.DragEvent, nodeType: string) => {
    event.dataTransfer.setData('application/reactflow', nodeType);
    event.dataTransfer.effectAllowed = 'move';
  };

  return (
    <Box sx={{
      width: 250,
      borderRight: '1px solid',
      borderColor: 'divider',
      bgcolor: 'background.paper',
      display: 'flex',
      flexDirection: 'column',
      height: '100%',
      maxHeight: '100%',
      overflow: 'hidden',
      boxSizing: 'border-box',
      p: 2.5
    }}>
      <Typography variant="subtitle2" sx={{ fontWeight: 800, color: 'text.primary', mb: 0.5 }}>
        Node Catalog
      </Typography>
      <Typography variant="caption" color="text.secondary" sx={{ mb: 2, display: 'block' }}>
        Drag and drop elements onto the canvas grid to map the workflow logic.
      </Typography>
      <Divider sx={{ mb: 2 }} />

      <Box sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 1.5,
        overflowY: 'auto',
        flexGrow: 1,
        minHeight: 0,
        scrollbarWidth: 'none',
        '&::-webkit-scrollbar': {
          display: 'none'
        },
        msOverflowStyle: 'none'
      }}>
        {items.map((item) => (
          <Card
            key={item.type}
            draggable
            onDragStart={(e) => onDragStart(e, item.type)}
            sx={{
              bgcolor: (theme) => theme.palette.mode === 'dark' ? 'rgba(255,255,255,0.02)' : 'rgba(0,0,0,0.01)',
              border: '1.5px dashed',
              borderColor: 'divider',
              borderRadius: 2.5,
              cursor: 'grab',
              flexShrink: 0,
              transition: 'all 0.15s ease',
              '&:hover': {
                borderColor: item.color,
                bgcolor: (theme) => theme.palette.mode === 'dark' ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.03)',
                transform: 'translateY(-1px)'
              },
              '&:active': {
                cursor: 'grabbing'
              }
            }}
          >
            <CardContent sx={{ p: 1.5, '&:last-child': { pb: 1.5 }, display: 'flex', gap: 1.5, alignItems: 'center' }}>
              <Box sx={{
                width: 32,
                height: 32,
                borderRadius: 2,
                bgcolor: (theme) => theme.palette.mode === 'dark' ? 'rgba(255,255,255,0.03)' : 'rgba(0,0,0,0.02)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}>
                {item.icon}
              </Box>
              <Box>
                <Typography variant="subtitle2" sx={{ fontWeight: 700, fontSize: '12px' }}>
                  {item.label}
                </Typography>
                <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', fontSize: '9px', lineHeight: 1.2 }}>
                  {item.description}
                </Typography>
              </Box>
            </CardContent>
          </Card>
        ))}
      </Box>
    </Box>
  );
};
