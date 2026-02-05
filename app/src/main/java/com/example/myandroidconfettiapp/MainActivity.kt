package com.example.myandroidconfettiapp

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myandroidconfettiapp.ui.theme.MyAndroidConfettiAppTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private val ConfettiColors = longArrayOf(
    0xFFFF6B6BL, // Red
    0xFF4ECDC4L, // Teal
    0xFFFFE66DL, // Yellow
    0xFF95E1D3L, // Mint
    0xFFF38181L, // Coral
    0xFFAA96DAL, // Purple
    0xFFFF9FF3L, // Pink
    0xFF54A0FFL, // Blue
    0xFF5F27CDL, // Deep Purple
    0xFF00D2D3L, // Cyan
    0xFFFF9F43L, // Orange
    0xFF10AC84L, // Green
)

private const val MAX_DT = 0.025f // Cap at ~40fps (25ms) - if frame takes longer, slow-mo instead of jump
private const val GRAVITY = 450f // Gentler gravity for longer falls
private const val TERMINAL_VELOCITY = 280f // Slower terminal velocity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyAndroidConfettiAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ConfettiScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

enum class ConfettiShape { PAPER, SQUARE, RIBBON, STAR, CIRCLE }

class MutableParticle(
    var x: Float,
    var y: Float,
    var velocityX: Float,
    var velocityY: Float,
    var colorLong: Long,
    var size: Float,
    var aspectRatio: Float,
    var rotation: Float,
    var rotationSpeed: Float,
    var flipSpeed: Float,
    var flipPhase: Float,
    var wobbleSpeed: Float,
    var wobbleAmplitude: Float,
    var wobblePhase: Float,
    var drag: Float,
    var shape: ConfettiShape,
    var alive: Boolean = true
)

class ParticlePool(initialCapacity: Int = 500) {
    private val pool = ArrayDeque<MutableParticle>(initialCapacity)
    
    fun obtain(
        x: Float, y: Float, vx: Float, vy: Float,
        colorLong: Long, size: Float, aspectRatio: Float,
        rotation: Float, rotationSpeed: Float,
        flipSpeed: Float, flipPhase: Float,
        wobbleSpeed: Float, wobbleAmplitude: Float, wobblePhase: Float,
        drag: Float, shape: ConfettiShape
    ): MutableParticle {
        return if (pool.isNotEmpty()) {
            pool.removeLast().apply {
                this.x = x
                this.y = y
                this.velocityX = vx
                this.velocityY = vy
                this.colorLong = colorLong
                this.size = size
                this.aspectRatio = aspectRatio
                this.rotation = rotation
                this.rotationSpeed = rotationSpeed
                this.flipSpeed = flipSpeed
                this.flipPhase = flipPhase
                this.wobbleSpeed = wobbleSpeed
                this.wobbleAmplitude = wobbleAmplitude
                this.wobblePhase = wobblePhase
                this.drag = drag
                this.shape = shape
                this.alive = true
            }
        } else {
            MutableParticle(
                x, y, vx, vy, colorLong, size, aspectRatio,
                rotation, rotationSpeed, flipSpeed, flipPhase,
                wobbleSpeed, wobbleAmplitude, wobblePhase, drag, shape
            )
        }
    }
    
    fun recycle(particle: MutableParticle) {
        particle.alive = false
        if (pool.size < 1000) {
            pool.addLast(particle)
        }
    }
}

class ConfettiEngine {
    private val particles = ArrayList<MutableParticle>(1000)
    private val pool = ParticlePool()
    private val reusablePath = Path()
    
    var screenHeight = 2000f
    var frameCounter by mutableLongStateOf(0L)
    
    fun addBurst(
        x: Float,
        y: Float,
        particleCount: IntRange = 60..90,
        speedMultiplier: Float = 1f
    ) {
        val count = Random.nextInt(particleCount.first, particleCount.last + 1)
        val twoPi = (Math.PI * 2).toFloat()
        
        repeat(count) {
            val angle = Random.nextFloat() * twoPi
            val speed = (Random.nextFloat() * 180f + 100f) * speedMultiplier
            val shape = ConfettiShape.entries[Random.nextInt(5)]
            val cosAngle = cos(angle)
            val sinAngle = sin(angle)
            
            val particle = pool.obtain(
                x = x,
                y = y,
                vx = cosAngle * speed,
                vy = sinAngle * speed - 240f * speedMultiplier,
                colorLong = ConfettiColors[Random.nextInt(ConfettiColors.size)],
                size = Random.nextFloat() * 10f + 8f,
                aspectRatio = if (shape == ConfettiShape.RIBBON) 
                    Random.nextFloat() * 2f + 3f 
                else 
                    Random.nextFloat() * 1.5f + 0.5f,
                rotation = Random.nextFloat() * 360f,
                rotationSpeed = Random.nextFloat() * 300f - 150f,
                flipSpeed = Random.nextFloat() * 5f + 2f,
                flipPhase = Random.nextFloat() * twoPi,
                wobbleSpeed = Random.nextFloat() * 2.5f + 1f,
                wobbleAmplitude = Random.nextFloat() * 15f + 5f,
                wobblePhase = Random.nextFloat() * twoPi,
                drag = Random.nextFloat() * 0.8f + 1.2f,
                shape = shape
            )
            particles.add(particle)
        }
    }
    
    fun update(deltaNanos: Long) {
        if (particles.isEmpty()) return
        
        // Simple capped delta-time: if frame is slow, we go slow-mo instead of jumping
        val dt = min(deltaNanos / 1_000_000_000f, MAX_DT)
        val screenBottom = screenHeight + 100f
        
        // Update all particles with capped dt - no accumulator, no catching up
        var writeIndex = 0
        for (readIndex in particles.indices) {
            val p = particles[readIndex]
            if (!p.alive) continue
            
            // Drag on horizontal velocity
            val dragDecay = exp(-p.drag * dt)
            p.velocityX *= dragDecay
            
            // Gravity with terminal velocity
            if (p.velocityY < TERMINAL_VELOCITY) {
                p.velocityY = min(p.velocityY + GRAVITY * dt, TERMINAL_VELOCITY)
            }
            
            // Wobble (sinusoidal horizontal motion)
            p.wobblePhase += p.wobbleSpeed * dt
            val wobble = sin(p.wobblePhase) * p.wobbleAmplitude * dt
            
            // Position update
            p.x += p.velocityX * dt + wobble
            p.y += p.velocityY * dt
            
            // Rotation and flip animation
            p.rotation += p.rotationSpeed * dt
            p.flipPhase += p.flipSpeed * dt
            
            // Check if off screen
            if (p.y > screenBottom) {
                p.alive = false
                pool.recycle(p)
            } else {
                // Compact: move alive particles to front
                if (writeIndex != readIndex) {
                    particles[writeIndex] = p
                }
                writeIndex++
            }
        }
        
        // Trim the list to remove dead particles efficiently
        while (particles.size > writeIndex) {
            particles.removeAt(particles.size - 1)
        }
        
        frameCounter++
    }
    
    fun draw(drawScope: DrawScope) {
        if (particles.isEmpty()) return
        
        with(drawScope) {
            for (i in particles.indices) {
                val p = particles[i]
                if (!p.alive) continue
                
                val flipValue = sin(p.flipPhase)
                val scaleX = abs(flipValue).coerceIn(0.15f, 1f)
                val color = Color(p.colorLong)
                
                rotate(degrees = p.rotation, pivot = Offset(p.x, p.y)) {
                    scale(scaleX = scaleX, scaleY = 1f, pivot = Offset(p.x, p.y)) {
                        when (p.shape) {
                            ConfettiShape.PAPER -> {
                                val width = p.size * p.aspectRatio
                                val height = p.size
                                val halfWidth = width * 0.5f
                                val halfHeight = height * 0.5f
                                drawRect(
                                    color = color,
                                    topLeft = Offset(p.x - halfWidth, p.y - halfHeight),
                                    size = Size(width, height)
                                )
                                drawRect(
                                    color = color.copy(alpha = 0.3f),
                                    topLeft = Offset(p.x - halfWidth, p.y - halfHeight),
                                    size = Size(width * 0.3f, height)
                                )
                            }
                            ConfettiShape.SQUARE -> {
                                val halfSize = p.size * 0.5f
                                drawRect(
                                    color = color,
                                    topLeft = Offset(p.x - halfSize, p.y - halfSize),
                                    size = Size(p.size, p.size)
                                )
                            }
                            ConfettiShape.RIBBON -> {
                                drawRibbon(p.x, p.y, p.size, p.aspectRatio, color, p.flipPhase, reusablePath)
                            }
                            ConfettiShape.STAR -> {
                                drawStar(p.x, p.y, p.size * 0.5f, color, reusablePath)
                            }
                            ConfettiShape.CIRCLE -> {
                                drawCircle(
                                    color = color,
                                    radius = p.size * 0.5f,
                                    center = Offset(p.x, p.y)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    val hasParticles: Boolean get() = particles.isNotEmpty()
}

fun vibrate(context: Context, durationMs: Long = 50, amplitude: Int = 100) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(durationMs)
    }
}

@Composable
fun ConfettiScreen(modifier: Modifier = Modifier) {
    val engine = remember { ConfettiEngine() }
    var tapCount by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF18181B))
    ) {
        val screenWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val screenHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
        
        engine.screenHeight = screenHeightPx
        
        // Shake detection
        DisposableEffect(Unit) {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            
            var lastShakeTime = 0L
            val shakeThreshold = 12f
            val shakeCooldown = 500L
            
            val shakeListener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    
                    val acceleration = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH
                    
                    if (acceleration > shakeThreshold) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastShakeTime > shakeCooldown) {
                            lastShakeTime = currentTime
                            vibrate(context, 100, 200)
                            tapCount += 5
                            repeat(5) {
                                engine.addBurst(
                                    x = Random.nextFloat() * screenWidthPx,
                                    y = Random.nextFloat() * screenHeightPx * 0.6f,
                                    particleCount = 40..60,
                                    speedMultiplier = 1.2f
                                )
                            }
                        }
                    }
                }
                
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            
            accelerometer?.let {
                sensorManager.registerListener(shakeListener, it, SensorManager.SENSOR_DELAY_UI)
            }
            
            onDispose {
                sensorManager.unregisterListener(shakeListener)
            }
        }
        
        // Single unified animation loop using LaunchedEffect for proper MonotonicFrameClock
        LaunchedEffect(Unit) {
            var lastFrameNanos = -1L
            
            while (isActive) {
                withFrameNanos { frameNanos ->
                    if (lastFrameNanos < 0) {
                        lastFrameNanos = frameNanos
                    }
                    val deltaNanos = frameNanos - lastFrameNanos
                    lastFrameNanos = frameNanos
                    // Only update if we have a valid positive delta
                    if (deltaNanos > 0) {
                        engine.update(deltaNanos)
                    }
                }
            }
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            vibrate(context, 30, 80)
                            tapCount++
                            engine.addBurst(offset.x, offset.y)
                        },
                        onLongPress = { offset ->
                            vibrate(context, 80, 255)
                            tapCount++
                            engine.addBurst(
                                x = offset.x,
                                y = offset.y,
                                particleCount = 150..200,
                                speedMultiplier = 1.5f
                            )
                        }
                    )
                }
        ) {
            Text(
                text = "🎉 TAP ANYWHERE! 🎉",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 100.dp)
            )
            
            Text(
                text = "💡 Long-press for mega burst\n📱 Shake for explosion!",
                color = Color(0xFF6B7280),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 60.dp)
            )
            
            if (tapCount > 0) {
                Text(
                    text = "Bursts: $tapCount 🎊",
                    color = Color(0xFF4ECDC4),
                    fontSize = 16.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp)
                )
            }
            
            // Use key() to force Canvas recreation when frame changes
            // This is the most reliable way to ensure the Canvas redraws
            key(engine.frameCounter) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    engine.draw(this)
                }
            }
        }
    }
}

fun DrawScope.drawRibbon(
    cx: Float, 
    cy: Float, 
    width: Float, 
    aspectRatio: Float, 
    color: Color, 
    phase: Float,
    path: Path
) {
    path.reset()
    val ribbonLength = width * aspectRatio
    val ribbonWidth = width * 0.4f
    val halfRibbonWidth = ribbonWidth * 0.5f
    val segments = 6 // Reduced from 8 for performance
    val segmentsF = segments.toFloat()
    val twoPi = (Math.PI * 2).toFloat()
    
    for (i in 0..segments) {
        val t = i / segmentsF
        val yOffset = (t - 0.5f) * ribbonLength
        val wave = sin(t * twoPi + phase) * halfRibbonWidth
        val px = cx + wave
        val py = cy + yOffset
        
        if (i == 0) {
            path.moveTo(px - halfRibbonWidth, py)
        } else {
            path.lineTo(px - halfRibbonWidth, py)
        }
    }
    
    for (i in segments downTo 0) {
        val t = i / segmentsF
        val yOffset = (t - 0.5f) * ribbonLength
        val wave = sin(t * twoPi + phase) * halfRibbonWidth
        path.lineTo(cx + wave + halfRibbonWidth, cy + yOffset)
    }
    path.close()
    
    drawPath(path, color)
}

fun DrawScope.drawStar(cx: Float, cy: Float, radius: Float, color: Color, path: Path) {
    path.reset()
    val points = 5
    val innerRadius = radius * 0.4f
    val halfPi = (Math.PI / 2).toFloat()
    val piOverPoints = (Math.PI / points).toFloat()
    
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) radius else innerRadius
        val angle = halfPi + i * piOverPoints
        val px = cx + r * cos(angle)
        val py = cy - r * sin(angle)
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    drawPath(path, color)
}
