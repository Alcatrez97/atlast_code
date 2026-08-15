import React from 'react';
import { Handle, Position } from '@xyflow/react';
import { Box, Typography, Card, CardContent } from '@mui/material';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import CancelIcon from '@mui/icons-material/Cancel';
import HelpOutlineIcon from '@mui/icons-material/Help';
import RuleIcon from '@mui/icons-material/Rule';
import ShoppingBagIcon from '@mui/icons-material/ShoppingBag';
import AccessAlarmIcon from '@mui/icons-material/AccessAlarm';
import TerminalIcon from '@mui/icons-material/Terminal';
import HourglassEmptyIcon from '@mui/icons-material/HourglassEmpty';
import AccountTreeIcon from '@mui/icons-material/AccountTree';
import './designer.css';
// Base node wrapper for styles - supports orientation
const NodeWrapper = ({ borderColor, selected, children }) => {
    return (
        <Card className="designer-custom-node" style={{ borderColor: selected ? '#EE2737' : borderColor }}>
            {children}
        </Card>
    );
};
// 1. Start Node
export const StartNode = ({ data, selected }) => {
    const isHoriz = data?.orientation === 'horizontal';
    const color = '#0DA05A';
    return (
        <Box sx={{ position: 'relative', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
            <Box sx={{
                width: 14,
                height: 14,
                borderRadius: '50%',
                bgcolor: 'background.paper',
                border: `2px solid ${selected ? '#EE2737' : color}`,
                boxShadow: selected ? '0 0 0 2px rgba(238,39,55,0.4)' : 'none',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                transition: 'all 0.2s'
            }}>
                <PlayArrowIcon sx={{ color, fontSize: 10 }} />
            </Box>
            <Handle type="source" position={isHoriz ? Position.Right : Position.Bottom} style={{ background: color, width: 6, height: 6 }} />
        </Box>
    );
};
// 2. End Node
export const EndNode = ({ data, selected }) => {
    const isHoriz = data?.orientation === 'horizontal';
    const color = '#B30E0E';
    return (
        <Box sx={{ position: 'relative', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
            <Handle type="target" position={isHoriz ? Position.Left : Position.Top} style={{ background: color, width: 6, height: 6 }} />
            <Box sx={{
                width: 14,
                height: 14,
                borderRadius: '50%',
                bgcolor: 'background.paper',
                border: `2px solid ${selected ? '#EE2737' : color}`,
                boxShadow: selected ? '0 0 0 2px rgba(238,39,55,0.4)' : 'none',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                transition: 'all 0.2s'
            }}>
                <CancelIcon sx={{ color, fontSize: 10 }} />
            </Box>
        </Box>
    );
};
// 3. Rule Evaluation Node
export const RuleNode = ({ data, selected }) => {
    const isHoriz = data?.orientation === 'horizontal';
    const color = '#00A9FF';
    return (
        <NodeWrapper borderColor="rgba(0, 169, 255, 0.4)" selected={selected} orientation={data?.orientation}>
            <Handle type="target" position={isHoriz ? Position.Left : Position.Top} style={{ background: color, width: 6, height: 6 }} />
            <CardContent className="designer-custom-node-content">
                <RuleIcon sx={{ color, fontSize: 10 }} />
                <Box sx={{ flexGrow: 1, minWidth: 0 }}>
                    <Typography className="designer-custom-node-label">
                        {data.label || 'Unconfigured Rule'}
                    </Typography>
                </Box>
            </CardContent>
            <Handle type="source" position={isHoriz ? Position.Right : Position.Bottom} style={{ background: color, width: 6, height: 6 }} />
        </NodeWrapper>
    );
};

export const DecisionNode = ({ data, selected }) => {
    const isHoriz = data?.orientation === 'horizontal';
    const color = '#FFC600';
    return (
        <NodeWrapper borderColor="rgba(255, 198, 0, 0.4)" selected={selected} orientation={data?.orientation}>
            <Handle type="target" position={isHoriz ? Position.Left : Position.Top} style={{ background: color, width: 6, height: 6 }} />
            <CardContent className="designer-custom-node-content">
                <HelpOutlineIcon sx={{ color, fontSize: 10 }} />
                <Box sx={{ flexGrow: 1, minWidth: 0 }}>
                    <Typography className="designer-custom-node-label">
                        {data.label || 'Branch Condition'}
                    </Typography>
                </Box>
            </CardContent>
            <Handle type="source" position={isHoriz ? Position.Right : Position.Bottom} style={{ background: color, width: 6, height: 6 }} />
        </NodeWrapper>
    );
};

export const BucketNode = ({ data, selected }) => {
    const isHoriz = data?.orientation === 'horizontal';
    const color = '#5F004B';
    return (
        <NodeWrapper borderColor="rgba(95, 0, 75, 0.4)" selected={selected} orientation={data?.orientation}>
            <Handle type="target" position={isHoriz ? Position.Left : Position.Top} style={{ background: color, width: 6, height: 6 }} />
            <CardContent className="designer-custom-node-content">
                <ShoppingBagIcon sx={{ color, fontSize: 10 }} />
                <Box sx={{ flexGrow: 1, minWidth: 0 }}>
                    <Typography className="designer-custom-node-label">
                        {data.label || 'Business Bucket'}
                    </Typography>
                </Box>
            </CardContent>
            <Handle type="source" position={isHoriz ? Position.Right : Position.Bottom} style={{ background: color, width: 6, height: 6 }} />
        </NodeWrapper>
    );
};

export const TimerNode = ({ data, selected }) => {
    const isHoriz = data?.orientation === 'horizontal';
    const color = '#DF720E';
    return (
        <NodeWrapper borderColor="rgba(223, 114, 14, 0.4)" selected={selected} orientation={data?.orientation}>
            <Handle type="target" position={isHoriz ? Position.Left : Position.Top} style={{ background: color, width: 6, height: 6 }} />
            <CardContent className="designer-custom-node-content">
                <AccessAlarmIcon sx={{ color, fontSize: 10 }} />
                <Box sx={{ flexGrow: 1, minWidth: 0 }}>
                    <Typography className="designer-custom-node-label">
                        {data.label || '10s Delay'}
                    </Typography>
                </Box>
            </CardContent>
            <Handle type="source" position={isHoriz ? Position.Right : Position.Bottom} style={{ background: color, width: 6, height: 6 }} />
        </NodeWrapper>
    );
};
// 7. Parallel Node
export const ParallelNode = ({ data, selected }) => {
    const isHoriz = data?.orientation === 'horizontal';
    const color = '#82838E';
    return (
        <Box sx={{ position: 'relative', display: 'flex', flexDirection: isHoriz ? 'row' : 'column', alignItems: 'center' }}>
            <Handle type="target" position={isHoriz ? Position.Left : Position.Top} style={{ background: color, width: 6, height: 6 }} />
            <Box sx={{
                width: isHoriz ? 5 : 50,
                height: isHoriz ? 50 : 5,
                bgcolor: 'background.paper',
                border: `1px solid ${selected ? '#EE2737' : color}`,
                boxShadow: 'none',
                borderRadius: 0.5
            }} />
            <Typography variant="caption" sx={{
                ml: isHoriz ? 0.5 : 0,
                mt: isHoriz ? 0 : 0.1,
                fontWeight: 700,
                fontSize: '6px',
                color,
                writingMode: isHoriz ? 'vertical-lr' : 'horizontal-tb'
            }}>PARALLEL SPLIT</Typography>
            <Handle type="source" position={isHoriz ? Position.Right : Position.Bottom} style={{ background: color, width: 6, height: 6 }} />
        </Box>
    );
};

// 8. Join Node
export const JoinNode = ({ data, selected }) => {
    const isHoriz = data?.orientation === 'horizontal';
    const color = '#82838E';
    return (
        <Box sx={{ position: 'relative', display: 'flex', flexDirection: isHoriz ? 'row' : 'column', alignItems: 'center' }}>
            <Handle type="target" position={isHoriz ? Position.Left : Position.Top} style={{ background: color, width: 6, height: 6 }} />
            <Box sx={{
                width: isHoriz ? 5 : 50,
                height: isHoriz ? 50 : 5,
                bgcolor: 'background.paper',
                border: `1px solid ${selected ? '#EE2737' : color}`,
                boxShadow: 'none',
                borderRadius: 0.5
            }} />
            <Typography variant="caption" sx={{
                ml: isHoriz ? 0.5 : 0,
                mt: isHoriz ? 0 : 0.1,
                fontWeight: 700,
                fontSize: '6px',
                color,
                writingMode: isHoriz ? 'vertical-lr' : 'horizontal-tb'
            }}>JOIN CONVERGE</Typography>
            <Handle type="source" position={isHoriz ? Position.Right : Position.Bottom} style={{ background: color, width: 6, height: 6 }} />
        </Box>
    );
};

// 9. Sub-Workflow Node
export const SubWorkflowNode = ({ data, selected }) => {
    const isHoriz = data?.orientation === 'horizontal';
    const color = '#00A9FF';
    return (
        <NodeWrapper borderColor="rgba(0, 169, 255, 0.4)" selected={selected} orientation={data?.orientation}>
            <Handle type="target" position={isHoriz ? Position.Left : Position.Top} style={{ background: color, width: 6, height: 6 }} />
            <CardContent className="designer-custom-node-content">
                <AccountTreeIcon sx={{ color, fontSize: 10 }} />
                <Box sx={{ flexGrow: 1, minWidth: 0 }}>
                    <Typography className="designer-custom-node-label">
                        {data.label || 'Sub-Workflow Call'}
                    </Typography>
                </Box>
            </CardContent>
            <Handle type="source" position={isHoriz ? Position.Right : Position.Bottom} style={{ background: color, width: 6, height: 6 }} />
        </NodeWrapper>
    );
};

// 10. Command Node
export const CommandNode = ({ data, selected }) => {
    const isHoriz = data?.orientation === 'horizontal';
    const color = '#EE2737';
    return (
        <NodeWrapper borderColor="rgba(238, 39, 55, 0.4)" selected={selected} orientation={data?.orientation}>
            <Handle type="target" position={isHoriz ? Position.Left : Position.Top} style={{ background: color, width: 6, height: 6 }} />
            <CardContent className="designer-custom-node-content">
                <TerminalIcon sx={{ color, fontSize: 10 }} />
                <Box sx={{ flexGrow: 1, minWidth: 0 }}>
                    <Typography className="designer-custom-node-label">
                        {data.label || 'Emit Command'}
                    </Typography>
                </Box>
            </CardContent>
            <Handle type="source" position={isHoriz ? Position.Right : Position.Bottom} style={{ background: color, width: 6, height: 6 }} />
        </NodeWrapper>
    );
};

// 11. Wait Event Node
export const WaitEventNode = ({ data, selected }) => {
    const isHoriz = data?.orientation === 'horizontal';
    const color = '#DF720E';
    return (
        <NodeWrapper borderColor="rgba(223, 114, 14, 0.4)" selected={selected} orientation={data?.orientation}>
            <Handle type="target" position={isHoriz ? Position.Left : Position.Top} style={{ background: color, width: 6, height: 6 }} />
            <CardContent className="designer-custom-node-content">
                <HourglassEmptyIcon sx={{ color, fontSize: 10 }} />
                <Box sx={{ flexGrow: 1, minWidth: 0 }}>
                    <Typography className="designer-custom-node-label">
                        {data.label || 'Wait for Event'}
                    </Typography>
                </Box>
            </CardContent>
            <Handle type="source" position={isHoriz ? Position.Right : Position.Bottom} style={{ background: color, width: 6, height: 6 }} />
        </NodeWrapper>
    );
};
// Mapping dictionary for React Flow node types config
export const nodeTypes = {
    START: StartNode,
    END: EndNode,
    RULE: RuleNode,
    DECISION: DecisionNode,
    BUCKET: BucketNode,
    TIMER: TimerNode,
    PARALLEL: ParallelNode,
    JOIN: JoinNode,
    SUB_WORKFLOW: SubWorkflowNode,
    COMMAND: CommandNode,
    WAIT_EVENT: WaitEventNode
};
