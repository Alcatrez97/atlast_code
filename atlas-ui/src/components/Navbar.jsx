import React from 'react';
import { AppBar, Toolbar, Typography, Box, Select, MenuItem, FormControl, Chip, Button, IconButton, InputBase } from '@mui/material';
import { Menu, Search, Bell, User, Layers } from 'lucide-react';
import { useWorkflowStore } from '../store/workflowStore.js';
export const Navbar = () => {
    const { activeRole, setActiveRole, currentView, setView, toggleSidebar } = useWorkflowStore();
    return (<AppBar position="sticky" sx={{
            bgcolor: '#FFFFFF',
            color: '#2F3043',
            borderBottom: '1px solid #EAEAEC',
            boxShadow: 'none',
            height: 64, // Increased height for top navigation
        }}>
      <Toolbar sx={{ minHeight: '64px !important', height: 64, px: '24px !important', display: 'flex', justifyContent: 'space-between' }}>
        
        {/* Left Side: Brand & Menu Toggle */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          <IconButton edge="start" aria-label="menu" onClick={toggleSidebar} size="small" sx={{ color: '#595969', '&:hover': { bgcolor: '#F4F4F4' } }}>
            <Menu size={20}/>
          </IconButton>

          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
            <Box sx={{
            bgcolor: '#EE2737', // Brand Crimson
            borderRadius: '6px',
            width: 32,
            height: 32,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center'
        }}>
              <Layers size={18} color="#ffffff"/>
            </Box>
            
            <Box sx={{ display: 'flex', flexDirection: 'column' }}>
              <Typography sx={{ fontWeight: 700, fontSize: '16px', color: '#2F3043', lineHeight: 1.2 }}>
                Atlas
              </Typography>
              <Typography sx={{ fontWeight: 500, fontSize: '11px', color: '#82838E', lineHeight: 1.2 }}>
                Workflow Engine
              </Typography>
            </Box>

            {/* Environment Badge */}
            <Chip label="PRODUCTION" size="small" sx={{
            ml: 2,
            bgcolor: 'rgba(0, 160, 90, 0.1)',
            color: '#00A05A',
            fontWeight: 700,
            fontSize: '10px',
            height: 20,
            borderRadius: '4px'
        }}/>
          </Box>
        </Box>

        {/* Center/Right Side Main Navigation & Utilities */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          
          <Box sx={{ display: 'flex', gap: 1 }}>
            <Button onClick={() => setView('dashboard')} sx={{
            color: currentView === 'dashboard' ? '#EE2737' : '#595969',
            fontWeight: 600,
            fontSize: '13px',
            height: 64,
            borderRadius: 0,
            px: 2,
            borderBottom: currentView === 'dashboard' ? '3px solid #EE2737' : '3px solid transparent',
            '&:hover': { bgcolor: 'transparent', color: '#EE2737' }
        }}>
              Dashboard
            </Button>
            <Button onClick={() => setView('manageWorkflows')} sx={{
            color: currentView === 'manageWorkflows' ? '#EE2737' : '#595969',
            fontWeight: 600,
            fontSize: '13px',
            height: 64,
            borderRadius: 0,
            px: 2,
            borderBottom: currentView === 'manageWorkflows' ? '3px solid #EE2737' : '3px solid transparent',
            '&:hover': { bgcolor: 'transparent', color: '#EE2737' }
        }}>
              Manage Workflows
            </Button>
          </Box>

          {/* Search Box */}
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            bgcolor: '#F4F4F4',
            borderRadius: '6px',
            px: 1.5,
            py: 0.5,
            width: 200,
            border: '1px solid transparent',
            '&:hover, &:focus-within': {
                borderColor: '#ACACB4',
                bgcolor: '#FFFFFF'
            }
        }}>
            <Search size={16} color="#82838E"/>
            <InputBase placeholder="Search..." sx={{ ml: 1, flex: 1, fontSize: '13px', color: '#2F3043' }}/>
          </Box>

          {/* Governance Role Selector */}
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <FormControl size="small" variant="outlined" sx={{ minWidth: 120 }}>
              <Select id="role-select" value={activeRole} onChange={(e) => setActiveRole(e.target.value)} sx={{
            height: 32,
            fontSize: '13px',
            fontWeight: 500,
            color: '#2F3043',
            bgcolor: '#FFFFFF',
            borderRadius: '6px',
            '.MuiOutlinedInput-notchedOutline': {
                borderColor: '#EAEAEC',
            },
            '&:hover .MuiOutlinedInput-notchedOutline': {
                borderColor: '#ACACB4',
            },
            '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
                borderColor: '#EE2737', // Crimson focus
            },
        }}>
                <MenuItem value="Author" sx={{ fontSize: '13px', py: 1 }}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <Chip size="small" label="AU" sx={{ bgcolor: '#0CADEF', color: '#fff', fontSize: '10px', fontWeight: 600, height: 20, borderRadius: '4px' }}/>
                    Author
                  </Box>
                </MenuItem>
                <MenuItem value="Reviewer" sx={{ fontSize: '13px', py: 1 }}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <Chip size="small" label="RV" sx={{ bgcolor: '#FFC600', color: '#111622', fontSize: '10px', fontWeight: 600, height: 20, borderRadius: '4px' }}/>
                    Reviewer
                  </Box>
                </MenuItem>
                <MenuItem value="Publisher" sx={{ fontSize: '13px', py: 1 }}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <Chip size="small" label="PB" sx={{ bgcolor: '#2F3043', color: '#fff', fontSize: '10px', fontWeight: 600, height: 20, borderRadius: '4px' }}/>
                    Publisher
                  </Box>
                </MenuItem>
              </Select>
            </FormControl>
          </Box>

          {/* Utilities */}
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <IconButton size="small" sx={{ color: '#595969', '&:hover': { bgcolor: '#F4F4F4', color: '#2F3043' } }}>
              <Bell size={18}/>
            </IconButton>
            <IconButton size="small" sx={{ color: '#595969', '&:hover': { bgcolor: '#F4F4F4', color: '#2F3043' } }}>
              <User size={18}/>
            </IconButton>
          </Box>

        </Box>
      </Toolbar>
    </AppBar>);
};
