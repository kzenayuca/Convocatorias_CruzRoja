package org.cruzroja.bc2_convocatorias.aplicacion.interfaces;

import org.cruzroja.bc2_convocatorias.aplicacion.dto.ConvocatoriaDTO;
import java.io.IOException;
import java.util.List;
import org.cruzroja.bc2_convocatorias.aplicacion.dto.GuardarConvocatoriaComando;

public interface IConvocatoriaServicio {
    List<ConvocatoriaDTO> listarVerificacionMinimos() throws IOException;
    ConvocatoriaDTO obtener(String codigo) throws IOException;
    ConvocatoriaDTO definirMinimo(String codigo, int minimo) throws IOException;
    ConvocatoriaDTO definirHorario(String codigo, String horaInicio, String horaFin)
            throws IOException;
    ConvocatoriaDTO confirmar(String codigo) throws IOException;
    ConvocatoriaDTO publicar(GuardarConvocatoriaComando comando) throws IOException;
    ConvocatoriaDTO editar(String codigo, GuardarConvocatoriaComando comando) throws IOException;
    void eliminar(String codigo) throws IOException;
    List<ConvocatoriaDTO> filtrarPorPerfil(String perfil) throws IOException;
}
