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
import './designer.css';

const items = [
    {
        type: 'START',
        label: 'Start Node',
        description: 'Entry point of the workflow execution.',
        icon: <PlayArrowIcon sx={{ color: '#10b981' }}/>,
        color: '#10b981'
    },
    {
        type: 'END',
        label: 'End Node',
        description: 'Terminates workflow execution.',
        icon: <CancelIcon sx={{ color: '#ef4444' }}/>,
        color: '#ef4444'
    },
    {
        type: 'RULE',
        label: 'Rule Eval',
        description: 'Evaluates dynamic conditional rule expressions.',
        icon: <RuleIcon sx={{ color: '#6366f1' }}/>,
        color: '#6366f1'
    },
    {
        type: 'DECISION',
        label: 'Decision Branch',
        description: 'Decides routing edge based on variable evaluations.',
        icon: <HelpOutlineIcon sx={{ color: '#f59e0b' }}/>,
        color: '#f59e0b'
    },
    {
        type: 'BUCKET',
        label: 'Bucket Task',
        description: 'Represents a core business action or SLA target.',
        icon: <ShoppingBagIcon sx={{ color: '#a855f7' }}/>,
        color: '#a855f7'
    },
    {
        type: 'TIMER',
        label: 'Timer Delay',
        description: 'Stalls workflow execution for a relative period.',
        icon: <AccessAlarmIcon sx={{ color: '#14b8a6' }}/>,
        color: '#14b8a6'
    },
    {
        type: 'PARALLEL',
        label: 'Parallel Split',
        description: 'Branches execution path in parallel pathways.',
        icon: <CallSplitIcon sx={{ color: '#f97316' }}/>,
        color: '#f97316'
    },
    {
        type: 'JOIN',
        label: 'Join Merge',
        description: 'Waits and merges parallel execution pathways.',
        icon: <CallMergeIcon sx={{ color: '#f97316' }}/>,
        color: '#f97316'
    },
    {
        type: 'SUB_WORKFLOW',
        label: 'Sub-Workflow',
        description: 'Executes a nested workflow instance sub-task.',
        icon: <AccountTreeIcon sx={{ color: '#3b82f6' }}/>,
        color: '#3b82f6'
    },
    {
        type: 'COMMAND',
        label: 'Command Node',
        description: 'Emits a command payload to trigger external actions.',
        icon: <TerminalIcon sx={{ color: '#38bdf8' }}/>,
        color: '#38bdf8'
    },
    {
        type: 'WAIT_EVENT',
        label: 'Wait Event Node',
        description: 'Pauses execution until matching event is received.',
        icon: <HourglassEmptyIcon sx={{ color: '#f59e0b' }}/>,
        color: '#f59e0b'
    }
];

export const NodeCatalog = () => {
    const onDragStart = (event, nodeType) => {
        event.dataTransfer.setData('application/reactflow', nodeType);
        event.dataTransfer.effectAllowed = 'move';
    };

    return (
        <Box className="designer-catalog-container">
            <Typography variant="subtitle2" className="designer-catalog-title">
                Node Catalog
            </Typography>
      <Typography variant="caption" className="designer-catalog-caption">
        Drag and drop elements onto the canvas grid to map the workflow logic.
      </Typography>
      <Divider sx={{ mb: 2 }}/>

      <Box className="designer-catalog-items">
        {items.map((item) => (
          <Card
            key={item.type}
            draggable
            onDragStart={(e) => onDragStart(e, item.type)}
            className="designer-catalog-card"
          >
            <CardContent className="designer-catalog-card-content">
              <Box className="designer-catalog-icon-box">
                {item.icon}
              </Box>
              <Box>
                <Typography variant="subtitle2" sx={{ fontWeight: 700, fontSize: '10px' }}>
                  {item.label}
                </Typography>
              </Box>
            </CardContent>
          </Card>
        ))}
      </Box>
    </Box>
  );
};
