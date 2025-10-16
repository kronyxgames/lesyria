# Lesyria Commands Reference

This document lists all available commands in the Lesyria server.

## Player Commands

### /home
Teleport to your set home location.

- Usage: `/home`
- Permission: None

### /home set
Set your current location as home.

- Usage: `/home set`
- Permission: None

### /spawn
Teleport to the world spawn point.

- Usage: `/spawn`
- Permission: None

### /spawn [city]
Teleport to a major city.

- Usage: `/spawn <city>`
- Cities: paris, london, tokyo
- Permission: None

### /eco balance
Check your Lesyria Coins balance.

- Usage: `/eco balance`
- Permission: None

### /eco buy <item> <quantity>
Buy items from the global market.

- Usage: `/eco buy <item> <qty>`
- Items: diamond, iron_ingot, coal, etc.
- Prices: diamond=100, iron_ingot=20, coal=5 (per unit)
- Permission: None

### /eco sell <item> <quantity>
Sell items to the global market.

- Usage: `/eco sell <item> <qty>`
- Sell price: 80% of buy price
- Permission: None

### /quest start <quest_name>
Start a quest.

- Usage: `/quest start <quest_name>`
- Available quests: gather_wood, mine_coal
- Permission: None

### /quest complete <quest_name>
Complete a quest if requirements are met.

- Usage: `/quest complete <quest_name>`
- Permission: None

## Admin Commands

None implemented yet. Use standard Minecraft/Paper commands like `/op`, `/ban`, etc.

## NPC Interactions

- **Quest Master**: Right-click to get quest information (not implemented yet; use /quest commands).
- **Shopkeeper**: Right-click to open shop GUI (not implemented yet; use /eco commands).

## Command Permissions

All player commands have no permission requirements. Admins can restrict via plugins like LuckPerms.

## Examples

- Set home: `/home set`
- Go home: `/home`
- Check money: `/eco balance`
- Buy diamonds: `/eco buy diamond 5`
- Start wood quest: `/quest start gather_wood`
- Complete quest: `/quest complete gather_wood` (after gathering 10 oak logs)