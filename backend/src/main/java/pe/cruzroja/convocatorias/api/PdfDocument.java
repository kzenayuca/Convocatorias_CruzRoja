package pe.cruzroja.convocatorias.api;

import java.io.*;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.springframework.http.*;

/** PDF descargable real, sin ventana de impresión ni servicios externos. */
public final class PdfDocument {
    private PdfDocument() { }
    public static ResponseEntity<byte[]> download(String filename,String title,List<String> lines) throws IOException {
        try(var document=new PDDocument(); var output=new ByteArrayOutputStream()) {
            var font=new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            var all=new ArrayList<String>(); all.add(title); all.add("Cruz Roja - Arequipa");
            all.add("Generado: "+Store.now().withNano(0)); all.add(""); all.addAll(lines);
            PDPageContentStream stream=null; int y=0;
            try {
                for(String line:all) {
                    String clean=line==null?"":line.replaceAll("[\\p{Cntrl}]"," ");
                    var safe=new StringBuilder();
                    for(char ch:clean.toCharArray()) { try { font.encode(String.valueOf(ch)); safe.append(ch); } catch(IllegalArgumentException e) { safe.append('?'); } }
                    String remaining=safe.toString();
                    do {
                        int length=remaining.length();
                        while(length>0 && font.getStringWidth(remaining.substring(0,length))/1000*11>510) length--;
                        if(length<remaining.length()) { int space=remaining.lastIndexOf(' ',length); if(space>20) length=space; }
                        String chunk=remaining.substring(0,length); remaining=remaining.substring(length).stripLeading();
                        if(y<60) {
                            if(stream!=null) stream.close();
                            var page=new PDPage(); document.addPage(page); stream=new PDPageContentStream(document,page); y=740;
                        }
                        stream.setNonStrokingColor(y==740?new java.awt.Color(156,38,56):new java.awt.Color(38,50,56));
                        stream.beginText(); stream.setFont(font,11); stream.newLineAtOffset(45,y); stream.showText(chunk); stream.endText(); y-=17;
                    } while(!remaining.isEmpty());
                }
            } finally { if(stream!=null) stream.close(); }
            document.save(output);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header("Cache-Control","no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(filename).build().toString()).body(output.toByteArray());
        }
    }
}
