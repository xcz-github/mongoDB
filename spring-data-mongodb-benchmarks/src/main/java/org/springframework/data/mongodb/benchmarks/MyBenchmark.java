/*
 * Copyright (c) 2014, Oracle America, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 *  * Neither the name of Oracle nor the names of its contributors may be used
 *    to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.springframework.data.mongodb.benchmarks;

import java.net.UnknownHostException;
import java.util.Collections;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.springframework.data.convert.EntityInstantiator;
import org.springframework.data.convert.EntityInstantiators;
import org.springframework.data.mapping.PreferredConstructor.Parameter;
import org.springframework.data.mapping.model.ParameterValueProvider;
import org.springframework.data.mongodb.core.SimpleMongoDbFactory;
import org.springframework.data.mongodb.core.convert.DbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.BasicMongoPersistentEntity;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.mapping.MongoPersistentProperty;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

@State(Scope.Benchmark)
public class MyBenchmark {

	private final MongoMappingContext mappingContext;
	private final MappingMongoConverter converter;
	private final DBObject plainSource, sourceWithAddress;
	private final Customer withAddress;

	public MyBenchmark() {

		MongoClient mongo;

		try {
			mongo = new MongoClient();
		} catch (UnknownHostException e) {
			throw new RuntimeException(e);
		}

		this.mappingContext = new MongoMappingContext();
		this.mappingContext.setInitialEntitySet(Collections.singleton(Customer.class));
		this.mappingContext.afterPropertiesSet();

		DbRefResolver dbRefResolver = new DefaultDbRefResolver(new SimpleMongoDbFactory(mongo, "benchmark"));

		this.converter = new MappingMongoConverter(dbRefResolver, mappingContext);
		this.plainSource = new BasicDBObject("firstname", "Dave").append("lastname", "Matthews");

		DBObject address = new BasicDBObject("zipCode", "ABCDE").append("city", "Some Place");

		this.sourceWithAddress = new BasicDBObject("firstname", "Dave").//
				append("lastname", "Matthews").//
				append("address", address);

		this.withAddress = new Customer("Dave", "Matthews", new Address("zipCode", "City"));
	}

	@Benchmark
	public void readPlainSource() {
		converter.read(Customer.class, plainSource);
	}

	@Benchmark
	public void readSourceWithAddress() {
		converter.read(Customer.class, sourceWithAddress);
	}

	@Benchmark
	// @Test
	public void writeSourceWithAddress() {
		converter.write(withAddress, new BasicDBObject());
	}

	public void foo() {

		BasicMongoPersistentEntity<?> entity = mappingContext.getPersistentEntity(Customer.class);

		EntityInstantiators instantiators = new EntityInstantiators();
		EntityInstantiator instantiator = instantiators.getInstantiatorFor(entity);

		instantiator.createInstance(entity, new ParameterValueProvider<MongoPersistentProperty>() {

			@Override
			public <T> T getParameterValue(Parameter<T, MongoPersistentProperty> parameter) {
				return null;
			}
		});
	}

	@Benchmark
	public void testMethod() {

		DBObject addressSource = (DBObject) sourceWithAddress.get("address");
		Address address = new Address(addressSource.get("zipCode").toString(), addressSource.get("city").toString());

		foo(new Customer(sourceWithAddress.get("firstname").toString(), sourceWithAddress.get("lastname").toString(),
				address));
	}

	private void foo(Customer customer) {}

	public static void main(String[] args) throws RunnerException {

		Options opt = new OptionsBuilder().//
				include(MyBenchmark.class.getSimpleName()).//
				forks(1).//
				build();

		new Runner(opt).run();
	}
}
