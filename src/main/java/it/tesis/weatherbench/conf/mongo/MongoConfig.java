package it.tesis.weatherbench.conf.mongo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.WriteResultChecking;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import lombok.extern.slf4j.Slf4j;

/**
 * Configurazione esplicita di Spring Data MongoDB.
 * <p>
 * Modello operativo <b>stateless</b>: il {@link MongoTemplate} converte POJO Java in documenti BSON
 * (e viceversa) al momento della chiamata e non mantiene alcuno stato fra le operazioni:
 * <ul>
 *     <li>nessun persistence context / identity map (due letture = due istanze distinte);</li>
 *     <li>nessun dirty checking: una modifica al POJO viene persistita solo con un comando esplicito;</li>
 *     <li>nessun proxy lazy: i sotto-documenti sono materializzati interamente dal BSON.</li>
 * </ul>
 * Il {@code MongoClient}, il {@code MongoDatabaseFactory} e il {@code MongoMappingContext} sono
 * autoconfigurati da Spring Boot a partire da {@code spring.data.mongodb.*} (MONGO_HOST / MONGO_PORT).
 */
@Slf4j
@Configuration
@EnableMongoRepositories(basePackages = MongoConfig.REPOSITORY_PACKAGE, mongoTemplateRef = "mongoTemplate")
public class MongoConfig {

    public static final String REPOSITORY_PACKAGE = "it.tesis.weatherbench.repository.mongo";

    /**
     * Converter Java &lt;-&gt; BSON. Il type mapper nullo elimina il campo {@code _class} da ogni
     * documento e sotto-documento: meno byte su disco/rete, mapping guidato solo dal tipo dichiarato.
     */
    @Bean
    public MappingMongoConverter mappingMongoConverter(MongoDatabaseFactory mongoDatabaseFactory,
            MongoMappingContext mongoMappingContext, MongoCustomConversions mongoCustomConversions) {
        MappingMongoConverter converter = new MappingMongoConverter(
                new DefaultDbRefResolver(mongoDatabaseFactory), mongoMappingContext);
        converter.setCustomConversions(mongoCustomConversions);
        converter.setTypeMapper(new DefaultMongoTypeMapper(null));
        return converter;
    }

    @Bean
    public MongoTemplate mongoTemplate(MongoDatabaseFactory mongoDatabaseFactory,
            MappingMongoConverter mappingMongoConverter) {
        MongoTemplate mongoTemplate = new MongoTemplate(mongoDatabaseFactory, mappingMongoConverter);
        mongoTemplate.setWriteResultChecking(WriteResultChecking.EXCEPTION);
        log.info("MongoTemplate initialized on database '{}'", mongoDatabaseFactory.getMongoDatabase().getName());
        return mongoTemplate;
    }
}
