package qic.launcher;

import java.awt.Graphics;
import java.awt.Image;

import javax.swing.JTextArea;
// http://stackoverflow.com/questions/8943878/how-to-set-the-background-image-for-a-textarea-on-click-of-a-button
public class TextAreaWithBackground extends JTextArea {
    private Image backgroundImage;

    public TextAreaWithBackground() {
        super();
        setOpaque(false);
    }

    public void setBackgroundImage(Image image) {
        this.backgroundImage = image;
        this.repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());

        if (backgroundImage != null) {
            g.drawImage(backgroundImage, 0, 0, this);
        }

        super.paintComponent(g);
    }
}