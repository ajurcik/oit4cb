package csdemo;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JPanel;
import javax.vecmath.Vector2f;
import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;

/**
 *
 * @author Adam Jurcik <xjurc@fi.muni.cz>
 */
public class SmallCirclesPlot extends JPanel {
    
    private Vector4f ai;
    private Vector4f aj;
    private Vector4f ak;
    private Vector3f vj;
    private Vector3f vk;
    private Vector3f nj;
    private Vector3f nk;
    private Vector3f h;
    private float rp;

    public SmallCirclesPlot(Vector4f ai, Vector4f aj, Vector4f ak,
            Vector3f vj, Vector3f vk, Vector3f nj, Vector3f nk, Vector3f h, float rp) {
        this.ai = ai;
        this.aj = aj;
        this.ak = ak;
        this.vj = vj;
        this.vk = vk;
        this.nj = nj;
        this.nk = nk;
        this.h = h;
        this.rp = rp;
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        Vector3f pi = new Vector3f(ai.x, ai.y, ai.z);
        Vector3f pj = new Vector3f(aj.x, aj.y, aj.z);
        Vector3f pk = new Vector3f(ak.x, ak.y, ak.z);
        
        Vector3f n = new Vector3f();
        n.cross(vj, vk);
        n.normalize();

        Vector3f b1 = new Vector3f(vj);
        b1.normalize();
        Vector3f b2 = new Vector3f();
        b2.cross(b1, n);

        Vector3f tbk = new Vector3f();
        tbk.sub(pk, pi);
        final Vector2f bk = new Vector2f();
        bk.x = b1.dot(tbk);
        bk.y = b2.dot(tbk);
        Vector3f tbj = new Vector3f();
        tbj.sub(pj, pi);
        final Vector2f bj = new Vector2f();
        bj.x = b1.dot(tbj);
        bj.y = b2.dot(tbj);

        final Vector2f bvj = new Vector2f();
        bvj.x = b1.dot(vj);
        bvj.y = b2.dot(vj);
        final Vector2f bvk = new Vector2f();
        bvk.x = b1.dot(vk);
        bvk.y = b2.dot(vk);

        final Vector2f bnj = new Vector2f();
        bnj.x = b1.dot(nj);
        bnj.y = b2.dot(nj);
        final Vector2f bnk = new Vector2f();
        bnk.x = b1.dot(nk);
        bnk.y = b2.dot(nk);

        final Vector2f bh = new Vector2f();
        bh.x = b1.dot(h);
        bh.y = b2.dot(h);
        
        g2d.translate(640, 480);
        float scale = 100f;
        Vector2f bi = new Vector2f();
        drawPoint(g2d, bi, "ai " + ai.w, scale);
        drawPoint(g2d, bj, "aj " + aj.w, scale);
        drawPoint(g2d, bk, "ak " + ak.w, scale);
        drawCircle(g2d, bi, ai.w + rp, scale);
        drawCircle(g2d, bj, aj.w + rp, scale);
        drawCircle(g2d, bk, ak.w + rp, scale);
        drawVector(g2d, bi, bvj, "vj", scale);
        drawVector(g2d, bi, bvk, "vk", scale);
        drawVector(g2d, bj, bnj, "nj", scale);
        drawVector(g2d, bk, bnk, "nk", scale);
        drawPoint(g2d, bh, "h", scale);
    }
    
    private void drawPoint(Graphics2D g2d, Vector2f p, String text, float scale) {
        int x = Math.round(scale * p.x);
        int y = Math.round(scale * p.y);
        g2d.drawLine(x - 3, y, x + 3, y);
        g2d.drawLine(x, y - 3, x, y + 3);
        g2d.drawString(text, x + 10, y + 10);
    }
    
    private void drawCircle(Graphics2D g2d, Vector2f c, float r, float scale) {
        int x = Math.round(scale * c.x);
        int y = Math.round(scale * c.y);
        int wh = Math.round(scale * 2f * r);
        g2d.drawOval(x - wh / 2, y - wh / 2, wh, wh);
    }
    
    private void drawVector(Graphics2D g2d, Vector2f p, Vector2f v, String text, float scale) {
        int x1 = Math.round(scale * p.x);
        int y1 = Math.round(scale * p.y);
        int x2 = Math.round(scale * (p.x + v.x));
        int y2 = Math.round(scale * (p.y + v.y));
        g2d.drawLine(x1, y1, x2, y2);
        g2d.setColor(Color.RED);
        g2d.drawLine((x1 + x2) / 2, (y1 + y2) / 2, x2, y2);
        g2d.setColor(Color.BLACK);
        g2d.drawString(text, (x1 + x2) / 2 + 10, (y1 + y2) / 2 + 10);
    }
    
}
