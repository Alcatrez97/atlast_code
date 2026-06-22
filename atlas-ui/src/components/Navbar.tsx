import React, { useState } from 'react';
import { AppBar, Toolbar, Typography, Box, Select, MenuItem, FormControl, InputLabel, Chip, Button, IconButton, Menu } from '@mui/material';
import MenuIcon from '@mui/icons-material/Menu';
import PaletteIcon from '@mui/icons-material/Palette';
import AccountCircleIcon from '@mui/icons-material/AccountCircle';
import AutoModeIcon from '@mui/icons-material/AutoMode';
import { useWorkflowStore } from '../store/workflowStore';

export const Navbar: React.FC = () => {
  const { activeRole, setActiveRole, currentView, setView, toggleSidebar, themeMode, setThemeMode } = useWorkflowStore();
  const [themeAnchorEl, setThemeAnchorEl] = useState<null | HTMLElement>(null);

  const handleOpenThemeMenu = (event: React.MouseEvent<HTMLElement>) => {
    setThemeAnchorEl(event.currentTarget);
  };

  const handleCloseThemeMenu = () => {
    setThemeAnchorEl(null);
  };

  const handleSelectTheme = (mode:
    | 'vi-light'
    | 'vi-dark'
    | 'cosmos-dark'
    | 'slate-dark'
    | 'nord-light'
    | 'emerald-light'
    | 'neural-dark'
    | 'cyberpunk-dark'
    | 'sunset-dark'
    | 'ruby-dark'
    | 'ocean-light'
    | 'lavender-light'
    | 'coffee-dark') => {
    setThemeMode(mode);
    handleCloseThemeMenu();
  };

  const getThemeLabel = (mode: string) => {
    switch (mode) {
      case 'vi-light':
        return 'Vi Light';

      case 'vi-dark':
        return 'Vi Dark';

      case 'cosmos-dark':
        return 'Cosmos Dark';

      case 'neural-dark':
        return 'Neural Dark';

      case 'slate-dark':
        return 'Slate Dark';

      case 'cyberpunk-dark':
        return 'Cyberpunk Dark';

      case 'sunset-dark':
        return 'Sunset Dark';

      case 'ruby-dark':
        return 'Ruby Dark';

      case 'coffee-dark':
        return 'Coffee Dark';

      case 'nord-light':
        return 'Nord Light';

      case 'ocean-light':
        return 'Ocean Light';

      case 'lavender-light':
        return 'Lavender Light';

      case 'emerald-light':
        return 'Emerald Light';

      default:
        return 'Vi Light';
    }
  };

  return (
    <AppBar position="sticky" sx={{
      bgcolor: 'background.paper',
      color: 'text.primary',
      backdropFilter: 'blur(12px)',
      borderBottom: '1px solid',
      borderColor: 'divider',
      boxShadow: 'none'
    }}>
      <Toolbar>
        {/* Menu toggle for left side navigation */}
        <IconButton
          edge="start"
          color="inherit"
          aria-label="menu"
          onClick={toggleSidebar}
          sx={{ mr: 2 }}
        >
          <MenuIcon />
        </IconButton>

        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mr: 4 }}>
          <Box sx={{
            background: (theme) => theme.palette.mode === 'dark' 
              ? 'linear-gradient(135deg, #E60000 0%, #FECB00 100%)' 
              : 'linear-gradient(135deg, #5E2750 0%, #E60000 100%)',
            borderRadius: 2,
            width: 38,
            height: 38,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: (theme) => `0 0 16px ${theme.palette.primary.main}40`
          }}>
            <AutoModeIcon sx={{ color: '#ffffff' }} />
          </Box>
          <Typography variant="h5" component="div" sx={{
            fontWeight: 800,
            letterSpacing: '0.5px',
            color: 'text.primary',
            display: 'flex',
            alignItems: 'center',
            gap: 1
          }}>
            Project Atlas
            <Box component="span" sx={{ 
              fontSize: '10px', 
              fontWeight: 700, 
              color: 'secondary.main', 
              letterSpacing: '2px', 
              border: '1px solid',
              borderColor: 'secondary.main', 
              padding: '1px 6px', 
              borderRadius: '4px' 
            }}>
              PLATFORM
            </Box>
          </Typography>
        </Box>

        {/* Top-Level Main Navigation */}
        <Box sx={{ display: 'flex', gap: 2, flexGrow: 1 }}>
          <Button
            size="small"
            onClick={() => setView('dashboard')}
            sx={{
              color: 'text.secondary',
              fontWeight: 700,
              borderBottom: currentView === 'dashboard' ? '2px solid' : '2px solid transparent',
              borderColor: 'primary.main',
              borderRadius: 0,
              px: 1,
              '&:hover': { color: 'secondary.main' }
            }}
          >
            Dashboard
          </Button>
          <Button
            size="small"
            onClick={() => setView('manageWorkflows')}
            sx={{
              color: 'text.secondary',
              fontWeight: 700,
              borderBottom: currentView === 'manageWorkflows' ? '2px solid' : '2px solid transparent',
              borderColor: 'primary.main',
              borderRadius: 0,
              px: 1,
              '&:hover': { color: 'primary.main' }
            }}
          >
            Manage Workflows
          </Button>
        </Box>

        <Box sx={{ display: 'flex', alignItems: 'center', gap: 3 }}>
          {/* Theme Selector Button */}
          <Box>
            <Button
              size="small"
              startIcon={<PaletteIcon />}
              onClick={handleOpenThemeMenu}
              sx={{ color: 'text.primary', fontWeight: 700 }}
            >
              {getThemeLabel(themeMode)}
            </Button>
            <Menu
              anchorEl={themeAnchorEl}
              open={Boolean(themeAnchorEl)}
              onClose={handleCloseThemeMenu}
              slotProps={{ paper: { sx: { bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider' } } }}
            >
              <MenuItem
                onClick={() => handleSelectTheme('vi-light')}
                selected={themeMode === 'vi-light'}
              >
                Vi Light
              </MenuItem>

              <MenuItem
                onClick={() => handleSelectTheme('vi-dark')}
                selected={themeMode === 'vi-dark'}
              >
                Vi Dark
              </MenuItem>

              <MenuItem
                onClick={() => handleSelectTheme('neural-dark')}
                selected={themeMode === 'neural-dark'}
              >
                Neural Dark
              </MenuItem>

              <MenuItem
                onClick={() => handleSelectTheme('cosmos-dark')}
                selected={themeMode === 'cosmos-dark'}
              >
                Cosmos Dark
              </MenuItem>

              <MenuItem
                onClick={() => handleSelectTheme('slate-dark')}
                selected={themeMode === 'slate-dark'}
              >
                Slate Dark
              </MenuItem>

              <MenuItem
                onClick={() => handleSelectTheme('cyberpunk-dark')}
                selected={themeMode === 'cyberpunk-dark'}
              >
                Cyberpunk Dark
              </MenuItem>

              <MenuItem
                onClick={() => handleSelectTheme('sunset-dark')}
                selected={themeMode === 'sunset-dark'}
              >
                Sunset Dark
              </MenuItem>

              <MenuItem
                onClick={() => handleSelectTheme('ruby-dark')}
                selected={themeMode === 'ruby-dark'}
              >
                Ruby Dark
              </MenuItem>

              <MenuItem
                onClick={() => handleSelectTheme('coffee-dark')}
                selected={themeMode === 'coffee-dark'}
              >
                Coffee Dark
              </MenuItem>

              <MenuItem
                onClick={() => handleSelectTheme('nord-light')}
                selected={themeMode === 'nord-light'}
              >
                Nord Light
              </MenuItem>

              <MenuItem
                onClick={() => handleSelectTheme('ocean-light')}
                selected={themeMode === 'ocean-light'}
              >
                Ocean Light
              </MenuItem>

              <MenuItem
                onClick={() => handleSelectTheme('lavender-light')}
                selected={themeMode === 'lavender-light'}
              >
                Lavender Light
              </MenuItem>

              <MenuItem
                onClick={() => handleSelectTheme('emerald-light')}
                selected={themeMode === 'emerald-light'}
              >
                Emerald Light
              </MenuItem>
            </Menu>
          </Box>

          {/* Governance Role Selector */}
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <AccountCircleIcon sx={{ color: 'text.secondary' }} />
            <FormControl size="small" variant="outlined" sx={{ minWidth: 140 }}>
              <InputLabel id="role-select-label" sx={{ color: 'text.secondary' }}>Governance Role</InputLabel>
              <Select
                labelId="role-select-label"
                id="role-select"
                value={activeRole}
                label="Governance Role"
                onChange={(e) => setActiveRole(e.target.value)}
                sx={{
                  color: 'text.primary',
                  '.MuiOutlinedInput-notchedOutline': {
                    borderColor: 'divider',
                  },
                  '&:hover .MuiOutlinedInput-notchedOutline': {
                    borderColor: 'primary.main',
                  },
                  '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
                    borderColor: 'primary.main',
                  },
                }}
              >
                <MenuItem value="Author">
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <Chip size="small" label="AU" sx={{ bgcolor: 'primary.dark', color: '#fff', fontSize: '10px', fontWeight: 700 }} />
                    Author
                  </Box>
                </MenuItem>
                <MenuItem value="Reviewer">
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <Chip size="small" label="RV" sx={{ bgcolor: 'warning.dark', color: '#fff', fontSize: '10px', fontWeight: 700 }} />
                    Reviewer
                  </Box>
                </MenuItem>
                <MenuItem value="Publisher">
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <Chip size="small" label="PB" sx={{ bgcolor: 'secondary.dark', color: '#fff', fontSize: '10px', fontWeight: 700 }} />
                    Publisher
                  </Box>
                </MenuItem>
              </Select>
            </FormControl>
          </Box>
        </Box>
      </Toolbar>
    </AppBar>
  );
};
