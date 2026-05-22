package com.demo.rag.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.BodyElementType;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;

@Service
public class DocumentParser {

    public String parse(byte[] bytes, String extension) throws IOException {
        String ext = extension.toLowerCase();
        return switch (ext) {
            case "txt", "text" -> new String(bytes, StandardCharsets.UTF_8);
            case "pdf" -> parsePdf(bytes);
            case "docx" -> parseDocx(bytes);
            default -> throw new IllegalArgumentException("Unsupported file type: " + extension);
        };
    }

    private String parsePdf(byte[] bytes) throws IOException {
        try (PDDocument document = PDDocument.load(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String parseDocx(byte[] bytes) throws IOException {
        StringBuilder text = new StringBuilder();
        try (ByteArrayInputStream fis = new ByteArrayInputStream(bytes);
             XWPFDocument document = new XWPFDocument(fis)) {
            List<IBodyElement> bodyElements = document.getBodyElements();
            for (IBodyElement element : bodyElements) {
                if (element.getElementType() == BodyElementType.PARAGRAPH) {
                    XWPFParagraph paragraph = (XWPFParagraph) element;
                    text.append(paragraph.getText()).append("\n");
                } else if (element.getElementType() == BodyElementType.TABLE) {
                    text.append(processTable((XWPFTable) element)).append("\n");
                }
            }
        }
        return text.toString();
    }

    private String processTable(XWPFTable table) {
        StringBuilder sb = new StringBuilder();
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                sb.append(cell.getText()).append("\t");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
