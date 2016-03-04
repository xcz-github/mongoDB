/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.mongodb.core.geo;

import static org.hamcrest.core.IsEqual.*;
import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.Id;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.config.AbstractMongoConfiguration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.mongodb.MongoClient;

import lombok.EqualsAndHashCode;

/**
 * @author Christoph Strobl
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
public class GeoAndGeoJsonInteropTests {

	@Configuration
	static class TestConfig extends AbstractMongoConfiguration {

		@Override
		protected String getDatabaseName() {
			return "database";
		}

		@Override
		@Bean
		public MongoClient mongo() throws Exception {
			return new MongoClient();
		}
	}

	@Autowired MongoTemplate template;
	@Autowired MongoClient client;

	/**
	 * @see DATAMONGO-1390
	 */
	@Test
	public void allowWriteAsGeoJsonPointReadAsLegacyPoint() {

		WithLegacy source = new WithLegacy();
		source.id = "legacy";
		source.point = new GeoJsonPoint(10d, 20d); // but store it in geosjon

		template.save(source);

		WithLegacy target = template.findOne(Query.query(Criteria.where("id").is(source.id)), WithLegacy.class);

		assertThat(target, equalTo(source));
	}

	/**
	 * @see DATAMONGO-1390
	 */
	@Test
	public void allowWriteAsLegacyPointReadAsGeoJsonPoint() {

		WithLegacy source = new WithLegacy();
		source.id = "legacy";
		source.point = new Point(10d, 20d);

		template.save(source);

		WithGeoJson target = template.findOne(Query.query(Criteria.where("id").is(source.id)), WithGeoJson.class);

		assertThat(target.point, equalTo(source.point));
	}

	@EqualsAndHashCode
	@Document(collection = "GeoAndGeoJsonInteropTests")
	static class WithLegacy {

		@Id String id;
		Point point;
	}

	@EqualsAndHashCode
	@Document(collection = "GeoAndGeoJsonInteropTests")
	static class WithGeoJson {

		@Id String id;
		GeoJsonPoint point;
	}

}
