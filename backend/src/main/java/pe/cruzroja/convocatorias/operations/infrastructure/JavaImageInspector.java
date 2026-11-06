package pe.cruzroja.convocatorias.operations.infrastructure;

import org.springframework.stereotype.Component;
import pe.cruzroja.convocatorias.operations.application.ImageInspector;
import static pe.cruzroja.convocatorias.shared.domain.Rules.*;

@Component
public class JavaImageInspector implements ImageInspector {
    public String validate(byte[] bytes) {
        valid(bytes.length>8,"La foto está vacía o no es válida.");
        boolean png=bytes[0]==(byte)137 && bytes[1]==80 && bytes[2]==78 && bytes[3]==71;
        boolean jpg=bytes[0]==(byte)255 && bytes[1]==(byte)216 && bytes[2]==(byte)255; valid(png || jpg,"Solo se admiten fotos JPG o PNG válidas.");
        try(var input=javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
            var readers=javax.imageio.ImageIO.getImageReaders(input); valid(readers.hasNext(),"La imagen no es válida."); var reader=readers.next();
            try { reader.setInput(input); valid((long)reader.getWidth(0)*reader.getHeight(0)<=40000000,"La foto admite hasta 40 megapíxeles."); valid(reader.read(0)!=null,"Imagen no válida."); } finally { reader.dispose(); }
        } catch(java.io.IOException e) { throw new IllegalArgumentException("La foto está dañada o no es una imagen JPG/PNG válida.",e); }
        return png?"image/png":"image/jpeg";
    }
}
