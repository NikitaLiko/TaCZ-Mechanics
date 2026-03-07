# TaCZ Mechanics

A military simulation enhancement mod for Minecraft 1.21.1 + NeoForge, extending the Timeless and Classics Zero (TaCZ) gun mod with realistic ballistics and immersive mechanics.

## Features

### 🎯 Ballistics & Penetration
- **Ricochet System**: Bullets can bounce off hard surfaces at shallow angles
- **Penetration System**: Bullets pierce through blocks with damage and velocity reduction
- Configurable angles, chances, speed multipliers, and max bounces

### 🔊 Distant Fire Sounds
- Dynamic gunfire sounds based on distance:
  - Close range: Full sound
  - Medium/Far/Very far: Progressive muffling and reverb
- Per-caliber configuration via JSON
- Auto-fallback to default sounds

### 💨 Bullet Whizz & Suppression
- **Bullet Whizz**: Fly-by sounds when bullets pass near players
- **Suppression System**: 
  - Visual effects (blur, vignette, desaturation)
  - Camera shake (aim punch)
  - Intensity based on proximity and bullet speed

### 🎮 Free Aim
- Independent gun movement from camera
- Configurable max deviation angles
- Smooth interpolation
- Optional crosshair following

### 🏃 Movement System
- **Crawling**: Reduced height/eye level for going prone
- **Sitting**: Adjusted dimensions for seated positions
- Configurable heights and eye levels

### ✨ Ricochet Effects
- Particle effects (sparks) when bullets ricochet
- Configurable per-block-type via JSON particle configs

## Configuration

All features are configurable via `tacz_mechanics-server.toml`:

```toml
# Global debug toggle
debug = false

[ricochet]
enabled = true
debug = false
maxBounces = 3
minSpeed = 15.0
minAngle = 15.0
chance = 0.6
speedMultiplier = 0.7

[penetration]
enabled = true
maxPenetrations = 3
minSpeed = 10.0
baseSpeedLoss = 0.3
damageMultiplier = 0.8

[suppression]
enabled = true
detectionRadius = 8.0
maxIntensity = 1.0
decayRate = 0.05

[freeAim]
enabled = true
maxAnglePitch = 8.0
maxAngleYaw = 12.0
lerpSpeed = 0.15

[movement]
enabled = true
crawlHeight = 0.6
crawlEyeHeight = 0.4
sitHeight = 1.0
sitEyeHeight = 0.8
```

## Data Packs

Create custom configs in your resource pack:

### Distant Fire Sounds
`data/<namespace>/tacz_mechanics/distant_fire/<caliber>.json`
```json
{
  "caliberId": "tacz:9mm",
  "closeSound": "namespace:distant/9mm_close",
  "midSound": "namespace:distant/9mm_mid",
  "farSound": "namespace:distant/9mm_far",
  "veryFarSound": "namespace:distant/9mm_veryfar",
  "closeMaxDistance": 100,
  "midMaxDistance": 250,
  "farMaxDistance": 450,
  "transitionBlocks": 20
}
```

### Bullet Particles
`data/<namespace>/tacz_mechanics/bullet_particles/<name>.json`
```json
{
  "type": "block",
  "blocks": ["minecraft:stone", "minecraft:iron_block"],
  "ricochet": [
    {
      "particle": "minecraft:small_flame",
      "count": 5,
      "speed": 0.3,
      "delta": {"x": 0.2, "y": 0.2, "z": 0.2}
    }
  ],
  "hit": [...],
  "pierce": [...]
}
```

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.x
- [TaCZ (Timeless and Classics Zero)](https://modrinth.com/mod/tacz)

## Building

```bash
./gradlew build
```

## License

All rights reserved.

## Credits

- **TaCZ**: Timeless and Classics Zero team for the base gun mod
- **WarBorn Pack**: Liko's custom gun pack included
