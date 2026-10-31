// ===================== AURORA =====================
#define AURORA                      // Aurora borealis at night
#define AURORA_LAYERS 2             // [1 2 3]
#define AURORA_BRIGHTNESS 1.0       // [0.5 0.75 1.0 1.25 1.5 2.0]
#define AURORA_COLOR 1              // [0 1 2]
#define AURORA_SPEED 1.0            // [0.5 1.0 1.5 2.0]
#define TWINKLE_STARS               // Stars shimmer and have slight colours
#define SHOOTING_STARS              // Occasional shooting star at night
#define SANTA_SLEIGH                // Rare glowing red-nose sleigh crossing the night sky
#define AURORA_REFLECTION           // Aurora glow on water
#define MOON_HALO                   // Glowing ring around the moon
#define MOON_HALO_BRIGHTNESS 1.0    // [0.5 1.0 1.5 2.0]

// ===================== SNOW =====================
#define SNOW_COVER                  // Fake snow on blocks that can see the sky
#define SNOW_COVERAGE 0.85          // [0.0 0.25 0.5 0.75 0.85 1.0]
#define SNOW_SPARKLE                // Glittering snow
#define SNOW_WEATHER                // Rain looks like snow
#define SNOW_FLAKES 1               // [0 1 2 3]
#define FLUFFY_LEAF_SNOW            // Thicker snow on tree leaves
#define FROSTED_GLASS               // Frosty edges on glass
#define ICE_SPARKLE                 // Sparkling ice
#define FESTIVE_MODE 1              // [0 1 2]

// ===================== CHRISTMAS DECOR =====================
#define TREE_LIGHTS                 // Glowing fairy lights on trees
//#define LIGHTS_ALL_TREES          // Lights on every leaf type, not just spruce
#define LIGHTS_BRIGHTNESS 1.0       // [0.5 0.75 1.0 1.5 2.0]
#define LIGHTS_DENSITY 0.25         // [0.1 0.15 0.25 0.35 0.5]
#define CHRISTMAS_CHESTS            // Chests and barrels become red presents with a gold ribbon
//#define CANDY_FENCES              // Fences get red/white candy-cane stripes
#define LANTERN_GLOW                // Lanterns, glowstone etc. glow
#define LANTERN_BRIGHTNESS 1.0      // [0.5 1.0 1.5 2.0]
#define WINDOW_GLOW                 // Glass glows warm at night when lit from inside
#define WINDOW_BRIGHTNESS 1.0       // [0.5 1.0 1.5 2.0]

// ===================== MIST & MOBS =====================
#define GROUND_MIST                 // Low-lying frosty mist (overworld)
#define MIST_DENSITY 1.0            // [0.5 1.0 1.5 2.0 3.0]
#define MIST_HEIGHT 72              // [64 68 72 80 96 128]
#define MOB_SNOW                    // Snow dusting on mobs' backs and heads
#define MOB_SNOW_AMOUNT 0.6         // [0.3 0.6 0.9]

// ===================== POST EFFECTS =====================
#define BLOOM                       // Soft glow
#define BLOOM_INTENSITY 1.0         // [0.5 0.75 1.0 1.5 2.0 3.0]
#define BLOOM_QUALITY 1             // [1 2]
#define COLOR_GRADE                 // Cool shadows, warm highlights
#define FROST_VIGNETTE 0.5          // [0.0 0.25 0.5 0.75 1.0]

// ===================== SHADOWS (optional, heavier) =====================
//#define SHADOWS
const int shadowMapResolution = 1024;   // [512 1024 2048 4096]
const float shadowDistance = 64.0;      // [32.0 48.0 64.0 96.0 128.0]
#define SHADOW_QUALITY 1            // [0 1 2]
#define SHADOW_STRENGTH 0.45        // [0.2 0.35 0.45 0.6 0.8]

// ===================== LIGHTING =====================
#define BRIGHTNESS 1.0              // [0.8 0.9 1.0 1.1 1.2 1.4]
#define WARM_LIGHT 0.6              // [0.0 0.3 0.6 1.0]
#define COLD_WATER                  // Icy tinted water
