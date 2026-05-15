package org.jopendocument.sample;

import java.io.File;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.util.Matrix;
import org.jopendocument.model.OpenDocument;
import org.jopendocument.renderer.ODTRenderer;

import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2D;
import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2DFontTextDrawer;
import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2DFontTextDrawerDefaultFonts;

/**
 * A simple example to convert an ODS file to PDF using Apache PDFBox
 * 
 */
public class PDFBoxGenerator {
    public static void main(String[] args) {

        long t1 = System.nanoTime();
        try {
            // FontFactory.defaultEmbedding = true;
            // Load the ODS file
            final OpenDocument doc = new OpenDocument();
            doc.loadFrom("template/invoice.ods");

            // Open the PDF document
            // Document document = new Document(PageSize.A4);

            File outFile = new File("invoice2.pdf");

            PDDocument document = new PDDocument();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            // Create a template and a Graphics2D object that corresponds with it
            PDRectangle pageSize = page.getMediaBox();
            int w = (int) (pageSize.getWidth() * 0.9);
            int h = (int) (pageSize.getHeight() * 0.95);

            PdfBoxGraphics2D g2 = new PdfBoxGraphics2D(document, w, h);

            PdfBoxGraphics2DFontTextDrawer fontTextDrawer = new PdfBoxGraphics2DFontTextDrawerDefaultFonts();
            final File dir = new File("fonts");
            if (dir.exists()) {
                fontTextDrawer.registerFontFromDirectory(dir);
            }
            g2.setFontTextDrawer(fontTextDrawer);

            // If you want to prevent copy/paste, you can use
            // g2 = tp.createGraphicsShapes(w, h, true, 0.9f);

            // Configure the renderer
            ODTRenderer renderer = new ODTRenderer(doc);
            renderer.setIgnoreMargins(true);
            renderer.setPaintMaxResolution(true);
            // Scale the renderer to fit width
            renderer.setResizeFactor(renderer.getPrintWidth() / w);
            // Render
            renderer.paintComponent(g2);
            g2.dispose();

            // Add our spreadsheet in the middle of the page
            float offsetX = (pageSize.getWidth() - w) / 2;
            float offsetY = (pageSize.getHeight() - h) / 2;

            // Close the PDF document
            PDFormXObject xform = g2.getXFormObject();

            final Matrix matrix = new Matrix();
            matrix.translate(offsetX, offsetY);
            final PDPageContentStream contentStream = new PDPageContentStream(document, page);
            contentStream.transform(matrix);

            contentStream.drawForm(xform);

            contentStream.close();

            document.save(outFile);
            document.close();

        } catch (Exception e1) {
            e1.printStackTrace();
        }
        long t2 = System.nanoTime();
        System.out.println("Time:" + (t2 - t1) / (1000 * 1000) + " ms");

    }
}
