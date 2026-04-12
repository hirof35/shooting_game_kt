import javax.swing.*
import java.awt.*
import java.awt.event.*

// 画面の状態を定義
enum class Scene { TITLE, PLAYING, GAMEOVER, CLEAR }

data class EnemyBullet(var x: Double, var y: Double, val angle: Double)
data class Particle(var x: Double, var y: Double, val vx: Double, val vy: Double, var life: Int)

class GamePanel : JPanel(), ActionListener {
    private var scene = Scene.TITLE

    // プレイヤー変数
    private var playerX = 250.0
    private var vx = 0.0
    private val friction = 0.85
    private var lives = 3
    private var isShooting = false

    // オブジェクトリスト
    private val bullets = mutableListOf<Point>()
    private val enemies = mutableListOf<Point>()
    private val enemyBullets = mutableListOf<EnemyBullet>()
    private val particles = mutableListOf<Particle>()

    // ボス変数
    private var score = 0
    private var bossHp = 50
    private var bossX = 150.0
    private var bossDir = 1
    private var isBossStage = false
    private var bossAttackTimer = 0

    private var leftPressed = false
    private var rightPressed = false
    private val timer = Timer(16, this)

    init {
        timer.start()
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (scene == Scene.TITLE || scene == Scene.GAMEOVER || scene == Scene.CLEAR) {
                    if (e.keyCode == KeyEvent.VK_SPACE) resetGame()
                    return
                }
                when (e.keyCode) {
                    KeyEvent.VK_LEFT -> leftPressed = true
                    KeyEvent.VK_RIGHT -> rightPressed = true
                    KeyEvent.VK_SPACE -> isShooting = true
                }
            }
            override fun keyReleased(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_LEFT -> leftPressed = false
                    KeyEvent.VK_RIGHT -> rightPressed = false
                    KeyEvent.VK_SPACE -> isShooting = false
                }
            }
        })
        isFocusable = true
    }

    private fun resetGame() {
        playerX = 250.0
        vx = 0.0
        lives = 3
        score = 0
        bossHp = 50
        isBossStage = false
        bullets.clear()
        enemies.clear()
        enemyBullets.clear()
        particles.clear()
        scene = Scene.PLAYING
    }

    private fun createExplosion(x: Double, y: Double) {
        repeat(15) {
            particles.add(Particle(x, y, (Math.random()-0.5)*8, (Math.random()-0.5)*8, 30))
        }
    }

    override fun actionPerformed(e: ActionEvent?) {
        if (scene != Scene.PLAYING) return

        // --- プレイヤー移動 ---
        if (leftPressed) vx -= 1.5
        if (rightPressed) vx += 1.5
        vx *= friction
        playerX += vx
        playerX = playerX.coerceIn(0.0, (width - 40).toDouble())

        // --- 自機弾 ---
        if (isShooting && System.currentTimeMillis() % 100 < 20) {
            bullets.add(Point(playerX.toInt() + 18, 490))
        }
        bullets.forEach { it.y -= 12 }; bullets.removeIf { it.y < 0 }

        // --- 敵の生成と移動 ---
        if (!isBossStage && Math.random() < 0.05) enemies.add(Point((0..width-30).random(), 0))
        enemies.forEach { it.y += 4 }

        // --- ボスロジック ---
        if (score >= 10 && !isBossStage) { isBossStage = true; enemies.clear() }
        if (isBossStage && bossHp > 0) {
            bossX += 3.0 * bossDir
            if (bossX < 50 || bossX > width - 250) bossDir *= -1
            bossAttackTimer++
            if (bossAttackTimer % 50 == 0) {
                repeat(10) { i -> enemyBullets.add(EnemyBullet(bossX + 100, 100.0, i * Math.PI * 2 / 10)) }
            }
        }

        // --- ボス弾移動 ---
        enemyBullets.forEach { it.x += Math.cos(it.angle)*5; it.y += Math.sin(it.angle)*5 }
        enemyBullets.removeIf { it.y > height || it.x < 0 || it.x > width }

        // --- エフェクト ---
        particles.forEach { it.x += it.vx; it.y += it.vy; it.life-- }; particles.removeIf { it.life <= 0 }

        // --- 当たり判定 ---
        val playerRect = Rectangle(playerX.toInt(), 500, 40, 40)

        // 自機弾 -> 敵/ボス
        bullets.removeIf { b ->
            val hitEnemy = enemies.removeIf { e -> Rectangle(e.x, e.y, 30, 30).contains(b.x, b.y) }
            if (hitEnemy) { score++; createExplosion(b.x.toDouble(), b.y.toDouble()) }
            var hitBoss = false
            if (isBossStage && bossHp > 0 && Rectangle(bossX.toInt(), 50, 200, 100).contains(b.x, b.y)) {
                bossHp--; hitBoss = true
                if (bossHp <= 0) scene = Scene.CLEAR
            }
            hitEnemy || hitBoss
        }

        // 敵弾/敵本体 -> 自機
        val hitMe = enemyBullets.removeIf { playerRect.contains(it.x.toInt(), it.y.toInt()) } ||
                enemies.removeIf { playerRect.intersects(Rectangle(it.x, it.y, 30, 30)) }
        if (hitMe) {
            lives--; createExplosion(playerX + 20, 520.0)
            if (lives <= 0) scene = Scene.GAMEOVER
        }

        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2d = g as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.color = Color.BLACK; g2d.fillRect(0, 0, width, height)

        when (scene) {
            Scene.TITLE -> {
                drawCenteredString(g2d, "KOTLIN SHOOTER", 200, 40, Color.CYAN)
                drawCenteredString(g2d, "Press SPACE to Start", 350, 20, Color.WHITE)
            }
            Scene.PLAYING -> {
                g2d.color = Color.CYAN; g2d.fillRect(playerX.toInt(), 500, 40, 40)
                g2d.color = Color.YELLOW; bullets.forEach { g2d.fillOval(it.x, it.y, 5, 12) }
                g2d.color = Color.RED; enemies.forEach { g2d.fillRect(it.x, it.y, 30, 30) }
                g2d.color = Color.ORANGE; enemyBullets.forEach { g2d.fillOval(it.x.toInt(), it.y.toInt(), 8, 8) }
                particles.forEach {
                    g2d.color = Color(255, 150, 0, (it.life * 8).coerceIn(0, 255))
                    g2d.fillOval(it.x.toInt(), it.y.toInt(), 5, 5)
                }
                if (isBossStage && bossHp > 0) {
                    g2d.color = Color.MAGENTA; g2d.fillRoundRect(bossX.toInt(), 50, 200, 100, 20, 20)
                    g2d.color = Color.GREEN; g2d.fillRect(bossX.toInt(), 30, bossHp * 4, 10)
                }
                g2d.color = Color.WHITE; g2d.drawString("SCORE: $score  LIVES: $lives", 20, 30)
            }
            Scene.GAMEOVER -> {
                drawCenteredString(g2d, "GAME OVER", 250, 50, Color.RED)
                drawCenteredString(g2d, "Final Score: $score", 320, 20, Color.WHITE)
                drawCenteredString(g2d, "Press SPACE to Title", 400, 15, Color.GRAY)
            }
            Scene.CLEAR -> {
                drawCenteredString(g2d, "MISSION COMPLETE!", 250, 45, Color.YELLOW)
                drawCenteredString(g2d, "Press SPACE to Title", 400, 15, Color.WHITE)
            }
        }
    }

    private fun drawCenteredString(g: Graphics2D, text: String, y: Int, size: Int, color: Color) {
        g.color = color
        g.font = Font("Arial", Font.BOLD, size)
        val metrics = g.fontMetrics
        val x = (width - metrics.stringWidth(text)) / 2
        g.drawString(text, x, y)
    }
}

fun main() {
    val frame = JFrame("Kotlin Shooter Master")
    frame.add(GamePanel()); frame.setSize(512, 650)
    frame.isResizable = false; frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.isVisible = true
}
