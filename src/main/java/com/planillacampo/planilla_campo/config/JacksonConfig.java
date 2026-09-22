package com.planillacampo.planilla_campo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;
import java.math.BigDecimal;

/**
 * Serializa BigDecimal como string en el JSON de respuesta para evitar pérdida
 * de precisión en clientes JavaScript (IEEE 754 soporta ~15 dígitos significativos,
 * insuficiente para importes de 18 enteros + 2 decimales).
 *
 * El deserializer acepta tanto string como número, por lo que el front puede
 * enviar cualquiera de los dos en los requests.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public SimpleModule bigDecimalAsStringModule() {
        SimpleModule module = new SimpleModule("BigDecimalAsString");
        module.addSerializer(BigDecimal.class, new BigDecimalStringSerializer());
        module.addDeserializer(BigDecimal.class, new BigDecimalStringDeserializer());
        return module;
    }

    private static class BigDecimalStringSerializer extends ValueSerializer<BigDecimal> {

        @Override
        public void serialize(BigDecimal value, JsonGenerator gen, SerializationContext ctxt)
                throws JacksonException {
            gen.writeString(value.toPlainString());
        }
    }

    private static class BigDecimalStringDeserializer extends ValueDeserializer<BigDecimal> {

        @Override
        public BigDecimal deserialize(JsonParser p, DeserializationContext ctxt)
                throws JacksonException {
            return new BigDecimal(p.getText().trim());
        }
    }
}
