/*
 * Copyright 2013-2016 the original author or authors.
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
package org.springframework.data.mongodb.core.aggregation;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.junit.Assume.*;
import static org.springframework.data.domain.Sort.Direction.*;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;
import static org.springframework.data.mongodb.core.aggregation.Fields.*;
import static org.springframework.data.mongodb.core.query.Criteria.*;
import static org.springframework.data.mongodb.test.util.IsBsonObject.*;

import java.io.BufferedInputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Scanner;

import org.bson.Document;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.geo.Metrics;
import org.springframework.data.mapping.model.MappingException;
import org.springframework.data.mongodb.core.CollectionCallback;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.Venue;
import org.springframework.data.mongodb.core.aggregation.AggregationTests.CarDescriptor.Entry;
import org.springframework.data.mongodb.core.index.GeospatialIndex;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.NearQuery;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.Person;
import org.springframework.data.util.Version;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;

/**
 * Tests for {@link MongoTemplate#aggregate(String, AggregationPipeline, Class)}.
 * 
 * @see DATAMONGO-586
 * @author Tobias Trelle
 * @author Thomas Darimont
 * @author Oliver Gierke
 * @author Christoph Strobl
 * @author Mark Paluch
 * @author Nikolay Bogdanov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:infrastructure.xml")
public class AggregationTests {

	private static final String INPUT_COLLECTION = "aggregation_test_collection";
	private static final Logger LOGGER = LoggerFactory.getLogger(AggregationTests.class);
	private static final Version TWO_DOT_FOUR = new Version(2, 4);
	private static final Version TWO_DOT_SIX = new Version(2, 6);
	private static final Version THREE_DOT_TWO = new Version(3, 2);

	private static boolean initialized = false;

	@Autowired MongoTemplate mongoTemplate;

	@Rule public ExpectedException exception = ExpectedException.none();
	private static Version mongoVersion;

	@Before
	public void setUp() {

		queryMongoVersionIfNecessary();
		cleanDb();
		initSampleDataIfNecessary();
	}

	private void queryMongoVersionIfNecessary() {

		if (mongoVersion == null) {
			org.bson.Document result = mongoTemplate.executeCommand("{ buildInfo: 1 }");
			mongoVersion = Version.parse(result.get("version").toString());
		}
	}

	@After
	public void cleanUp() {
		cleanDb();
	}

	private void cleanDb() {
		mongoTemplate.dropCollection(INPUT_COLLECTION);
		mongoTemplate.dropCollection(Product.class);
		mongoTemplate.dropCollection(UserWithLikes.class);
		mongoTemplate.dropCollection(DATAMONGO753.class);
		mongoTemplate.dropCollection(Data.class);
		mongoTemplate.dropCollection(DATAMONGO788.class);
		mongoTemplate.dropCollection(User.class);
		mongoTemplate.dropCollection(Person.class);
		mongoTemplate.dropCollection(Reservation.class);
		mongoTemplate.dropCollection(Venue.class);
		mongoTemplate.dropCollection(MeterData.class);
		mongoTemplate.dropCollection(LineItem.class);
		mongoTemplate.dropCollection(InventoryItem.class);
	}

	/**
	 * Imports the sample dataset (zips.json) if necessary (e.g. if it doen't exist yet). The dataset can originally be
	 * found on the mongodb aggregation framework example website:
	 * 
	 * @see http://docs.mongodb.org/manual/tutorial/aggregation-examples/.
	 */
	private void initSampleDataIfNecessary() {

		if (!initialized) {

			LOGGER.debug("Server uses MongoDB Version: {}", mongoVersion);

			mongoTemplate.dropCollection(ZipInfo.class);
			mongoTemplate.execute(ZipInfo.class, new CollectionCallback<Void>() {

				@Override
				public Void doInCollection(MongoCollection<Document> collection) throws MongoException, DataAccessException {

					Scanner scanner = null;
					try {
						scanner = new Scanner(new BufferedInputStream(new ClassPathResource("zips.json").getInputStream()));
						while (scanner.hasNextLine()) {
							String zipInfoRecord = scanner.nextLine();
							collection.insertOne(Document.parse(zipInfoRecord));
						}
					} catch (Exception e) {
						if (scanner != null) {
							scanner.close();
						}
						throw new RuntimeException("Could not load mongodb sample dataset!", e);
					}

					return null;
				}
			});

			long count = mongoTemplate.count(new Query(), ZipInfo.class);
			assertThat(count, is(29467L));

			initialized = true;
		}
	}

	@Test(expected = IllegalArgumentException.class)
	public void shouldHandleMissingInputCollection() {
		mongoTemplate.aggregate(newAggregation(), (String) null, TagCount.class);
	}

	@Test(expected = IllegalArgumentException.class)
	public void shouldHandleMissingAggregationPipeline() {
		mongoTemplate.aggregate(null, INPUT_COLLECTION, TagCount.class);
	}

	@Test(expected = IllegalArgumentException.class)
	public void shouldHandleMissingEntityClass() {
		mongoTemplate.aggregate(newAggregation(), INPUT_COLLECTION, null);
	}

	@Test
	public void shouldAggregate() {

		createTagDocuments();

		Aggregation agg = newAggregation( //
				project("tags"), //
				unwind("tags"), //
				group("tags") //
						.count().as("n"), //
				project("n") //
						.and("tag").previousOperation(), //
				sort(DESC, "n") //
		);

		AggregationResults<TagCount> results = mongoTemplate.aggregate(agg, INPUT_COLLECTION, TagCount.class);

		assertThat(results, is(notNullValue()));

		List<TagCount> tagCount = results.getMappedResults();

		assertThat(tagCount, is(notNullValue()));
		assertThat(tagCount.size(), is(3));

		assertTagCount("spring", 3, tagCount.get(0));
		assertTagCount("mongodb", 2, tagCount.get(1));
		assertTagCount("nosql", 1, tagCount.get(2));
	}

	@Test
	public void shouldAggregateEmptyCollection() {

		Aggregation aggregation = newAggregation(//
				project("tags"), //
				unwind("tags"), //
				group("tags") //
						.count().as("n"), //
				project("n") //
						.and("tag").previousOperation(), //
				sort(DESC, "n") //
		);

		AggregationResults<TagCount> results = mongoTemplate.aggregate(aggregation, INPUT_COLLECTION, TagCount.class);

		assertThat(results, is(notNullValue()));

		List<TagCount> tagCount = results.getMappedResults();

		assertThat(tagCount, is(notNullValue()));
		assertThat(tagCount.size(), is(0));
	}

	/**
	 * @see DATAMONGO-1391
	 */
	@Test
	public void shouldUnwindWithIndex() {

		assumeTrue(mongoVersion.isGreaterThanOrEqualTo(THREE_DOT_TWO));

		MongoCollection<Document> coll = mongoTemplate.getCollection(INPUT_COLLECTION);

		coll.insertOne(createDocument("Doc1", "spring", "mongodb", "nosql"));
		coll.insertOne(createDocument("Doc2"));

		Aggregation agg = newAggregation( //
				project("tags"), //
				unwind("tags", "n"), //
				project("n") //
						.and("tag").previousOperation(), //
				sort(DESC, "n") //
		);

		AggregationResults<TagCount> results = mongoTemplate.aggregate(agg, INPUT_COLLECTION, TagCount.class);

		assertThat(results, is(notNullValue()));

		List<TagCount> tagCount = results.getMappedResults();

		assertThat(tagCount, is(notNullValue()));
		assertThat(tagCount.size(), is(3));
	}

	/**
	 * @see DATAMONGO-1391
	 */
	@Test
	public void shouldUnwindPreserveEmpty() {

		assumeTrue(mongoVersion.isGreaterThanOrEqualTo(THREE_DOT_TWO));

		MongoCollection<Document> coll = mongoTemplate.getCollection(INPUT_COLLECTION);

		coll.insertOne(createDocument("Doc1", "spring", "mongodb", "nosql"));
		coll.insertOne(createDocument("Doc2"));

		Aggregation agg = newAggregation( //
				project("tags"), //
				unwind("tags", "n", true), //
				sort(DESC, "n") //
		);

		AggregationResults<Document> results = mongoTemplate.aggregate(agg, INPUT_COLLECTION, Document.class);

		assertThat(results, is(notNullValue()));

		List<Document> tagCount = results.getMappedResults();

		assertThat(tagCount, is(notNullValue()));
		assertThat(tagCount.size(), is(4));
		assertThat(tagCount.get(0), isBsonObject().containing("n", 2L));
		assertThat(tagCount.get(3), isBsonObject().notContaining("n"));
	}

	@Test
	public void shouldDetectResultMismatch() {

		createTagDocuments();

		Aggregation aggregation = newAggregation( //
				project("tags"), //
				unwind("tags"), //
				group("tags") //
						.count().as("count"), // count field not present
				limit(2) //
		);

		AggregationResults<TagCount> results = mongoTemplate.aggregate(aggregation, INPUT_COLLECTION, TagCount.class);

		assertThat(results, is(notNullValue()));

		List<TagCount> tagCount = results.getMappedResults();

		assertThat(tagCount, is(notNullValue()));
		assertThat(tagCount.size(), is(2));
		assertTagCount(null, 0, tagCount.get(0));
		assertTagCount(null, 0, tagCount.get(1));
	}

	@Test
	public void complexAggregationFrameworkUsageLargestAndSmallestCitiesByState() {
		/*
		 //complex mongodb aggregation framework example from http://docs.mongodb.org/manual/tutorial/aggregation-examples/#largest-and-smallest-cities-by-state
		db.zipInfo.aggregate( 
			{
			   $group: {
			      _id: {
			         state: '$state',
			         city: '$city'
			      },
			      pop: {
			         $sum: '$pop'
			      }
			   }
			},
			{
			   $sort: {
			      pop: 1,
			      '_id.state': 1,
			      '_id.city': 1
			   }
			},
			{
			   $group: {
			      _id: '$_id.state',
			      biggestCity: {
			         $last: '$_id.city'
			      },
			      biggestPop: {
			         $last: '$pop'
			      },
			      smallestCity: {
			         $first: '$_id.city'
			      },
			      smallestPop: {
			         $first: '$pop'
			      }
			   }
			},
			{
			   $project: {
			      _id: 0,
			      state: '$_id',
			      biggestCity: {
			         name: '$biggestCity',
			         pop: '$biggestPop'
			      },
			      smallestCity: {
			         name: '$smallestCity',
			         pop: '$smallestPop'
			      }
			   }
			},
			{
			   $sort: {
			      state: 1
			   }
			}
		)
		*/

		TypedAggregation<ZipInfo> aggregation = newAggregation(ZipInfo.class, //
				group("state", "city").sum("population").as("pop"), //
				sort(ASC, "pop", "state", "city"), //
				group("state") //
						.last("city").as("biggestCity") //
						.last("pop").as("biggestPop") //
						.first("city").as("smallestCity") //
						.first("pop").as("smallestPop"), //
				project() //
						.and("state").previousOperation() //
						.and("biggestCity").nested(bind("name", "biggestCity").and("population", "biggestPop")) //
						.and("smallestCity").nested(bind("name", "smallestCity").and("population", "smallestPop")), //
				sort(ASC, "state") //
		);

		assertThat(aggregation, is(notNullValue()));
		assertThat(aggregation.toString(), is(notNullValue()));

		AggregationResults<ZipInfoStats> result = mongoTemplate.aggregate(aggregation, ZipInfoStats.class);
		assertThat(result, is(notNullValue()));
		assertThat(result.getMappedResults(), is(notNullValue()));
		assertThat(result.getMappedResults().size(), is(51));

		ZipInfoStats firstZipInfoStats = result.getMappedResults().get(0);
		assertThat(firstZipInfoStats, is(notNullValue()));
		assertThat(firstZipInfoStats.id, is(nullValue()));
		assertThat(firstZipInfoStats.state, is("AK"));
		assertThat(firstZipInfoStats.smallestCity, is(notNullValue()));
		assertThat(firstZipInfoStats.smallestCity.name, is("CHEVAK"));
		assertThat(firstZipInfoStats.smallestCity.population, is(0));
		assertThat(firstZipInfoStats.biggestCity, is(notNullValue()));
		assertThat(firstZipInfoStats.biggestCity.name, is("ANCHORAGE"));
		assertThat(firstZipInfoStats.biggestCity.population, is(183987));

		ZipInfoStats lastZipInfoStats = result.getMappedResults().get(50);
		assertThat(lastZipInfoStats, is(notNullValue()));
		assertThat(lastZipInfoStats.id, is(nullValue()));
		assertThat(lastZipInfoStats.state, is("WY"));
		assertThat(lastZipInfoStats.smallestCity, is(notNullValue()));
		assertThat(lastZipInfoStats.smallestCity.name, is("LOST SPRINGS"));
		assertThat(lastZipInfoStats.smallestCity.population, is(6));
		assertThat(lastZipInfoStats.biggestCity, is(notNullValue()));
		assertThat(lastZipInfoStats.biggestCity.name, is("CHEYENNE"));
		assertThat(lastZipInfoStats.biggestCity.population, is(70185));
	}

	@Test
	public void findStatesWithPopulationOver10MillionAggregationExample() {
		/*
		 //complex mongodb aggregation framework example from 
		 http://docs.mongodb.org/manual/tutorial/aggregation-examples/#largest-and-smallest-cities-by-state
		 
		 db.zipcodes.aggregate( 
			 	{
				   $group: {
				      _id:"$state",
				      totalPop:{ $sum:"$pop"}
		 			 }
				},
				{ 
		 			$sort: { _id: 1, "totalPop": 1 } 
		 		},
				{
				   $match: {
				      totalPop: { $gte:10*1000*1000 }
				   }
				}
		)
		  */

		TypedAggregation<ZipInfo> agg = newAggregation(ZipInfo.class, //
				group("state") //
						.sum("population").as("totalPop"), //
				sort(ASC, previousOperation(), "totalPop"), //
				match(where("totalPop").gte(10 * 1000 * 1000)) //
		);

		assertThat(agg, is(notNullValue()));
		assertThat(agg.toString(), is(notNullValue()));

		AggregationResults<StateStats> result = mongoTemplate.aggregate(agg, StateStats.class);
		assertThat(result, is(notNullValue()));
		assertThat(result.getMappedResults(), is(notNullValue()));
		assertThat(result.getMappedResults().size(), is(7));

		StateStats stateStats = result.getMappedResults().get(0);
		assertThat(stateStats, is(notNullValue()));
		assertThat(stateStats.id, is("CA"));
		assertThat(stateStats.state, is(nullValue()));
		assertThat(stateStats.totalPopulation, is(29760021));
	}

	/**
	 * @see DATAMONGO-861
	 * @see https://docs.mongodb.com/manual/reference/operator/aggregation/cond/#example
	 */
	@Test
	public void aggregationUsingConditionalProjectionToCalculateDiscount() {

		/*
		db.inventory.aggregate(
		[
		  {
		     $project:
		       {
		         item: 1,
		         discount:
		           {
		             $cond: { if: { $gte: [ "$qty", 250 ] }, then: 30, else: 20 }
		           }
		       }
		  }
		]
		)
		 */

		mongoTemplate.insert(new InventoryItem(1, "abc1", 300));
		mongoTemplate.insert(new InventoryItem(2, "abc2", 200));
		mongoTemplate.insert(new InventoryItem(3, "xyz1", 250));

		TypedAggregation<InventoryItem> aggregation = newAggregation(InventoryItem.class, //
				project("item") //
						.and("discount")//
						.applyCondition(ConditionalOperator.newBuilder().when(Criteria.where("qty").gte(250)) //
								.then(30) //
								.otherwise(20)));

		assertThat(aggregation.toString(), is(notNullValue()));

		AggregationResults<Document> result = mongoTemplate.aggregate(aggregation, Document.class);
		assertThat(result.getMappedResults().size(), is(3));

		Document first = result.getMappedResults().get(0);
		assertThat(first.get("_id"), is((Object) 1));
		assertThat(first.get("discount"), is((Object) 30));

		Document second = result.getMappedResults().get(1);
		assertThat(second.get("_id"), is((Object) 2));
		assertThat(second.get("discount"), is((Object) 20));

		Document third = result.getMappedResults().get(2);
		assertThat(third.get("_id"), is((Object) 3));
		assertThat(third.get("discount"), is((Object) 30));
	}

	/**
	 * @see DATAMONGO-861
	 * @see https://docs.mongodb.com/manual/reference/operator/aggregation/ifNull/#example
	 */
	@Test
	public void aggregationUsingIfNullToProjectSaneDefaults() {

		/*
		db.inventory.aggregate(
		[
		  {
		     $project: {
		        item: 1,
		        description: { $ifNull: [ "$description", "Unspecified" ] }
		     }
		  }
		]
		)
		 */

		mongoTemplate.insert(new InventoryItem(1, "abc1", "product 1", 300));
		mongoTemplate.insert(new InventoryItem(2, "abc2", 200));
		mongoTemplate.insert(new InventoryItem(3, "xyz1", 250));

		TypedAggregation<InventoryItem> aggregation = newAggregation(InventoryItem.class, //
				project("item") //
						.and(ifNull("description", "Unspecified")) //
						.as("description")//
		);

		assertThat(aggregation.toString(), is(notNullValue()));

		AggregationResults<Document> result = mongoTemplate.aggregate(aggregation, Document.class);
		assertThat(result.getMappedResults().size(), is(3));

		Document first = result.getMappedResults().get(0);
		assertThat(first.get("_id"), is((Object) 1));
		assertThat(first.get("description"), is((Object) "product 1"));

		Document second = result.getMappedResults().get(1);
		assertThat(second.get("_id"), is((Object) 2));
		assertThat(second.get("description"), is((Object) "Unspecified"));
	}

	/**
	 * @see DATAMONGO-861
	 */
	@Test
	public void aggregationUsingConditionalProjection() {

		TypedAggregation<ZipInfo> aggregation = newAggregation(ZipInfo.class, //
				project() //
						.and("largePopulation")//
						.applyCondition(ConditionalOperator.newBuilder().when(Criteria.where("population").gte(20000)) //
								.then(true) //
								.otherwise(false)) //
						.and("population").as("population"));

		assertThat(aggregation, is(notNullValue()));
		assertThat(aggregation.toString(), is(notNullValue()));

		AggregationResults<Document> result = mongoTemplate.aggregate(aggregation, Document.class);
		assertThat(result.getMappedResults().size(), is(29467));

		Document firstZipInfoStats = result.getMappedResults().get(0);
		assertThat(firstZipInfoStats.get("largePopulation"), is((Object) false));
		assertThat(firstZipInfoStats.get("population"), is((Object) 6055));
	}

	/**
	 * @see DATAMONGO-861
	 */
	@Test
	public void aggregationUsingNestedConditionalProjection() {

		TypedAggregation<ZipInfo> aggregation = newAggregation(ZipInfo.class, //
				project() //
						.and("size")//
						.applyCondition(ConditionalOperator.newBuilder().when(Criteria.where("population").gte(20000)) //
								.then(ConditionalOperator.newBuilder().when(Criteria.where("population").gte(200000)).then("huge")
										.otherwise("small")) //
								.otherwise("small")) //
						.and("population").as("population"));

		assertThat(aggregation, is(notNullValue()));
		assertThat(aggregation.toString(), is(notNullValue()));

		AggregationResults<Document> result = mongoTemplate.aggregate(aggregation, Document.class);
		assertThat(result.getMappedResults().size(), is(29467));

		Document firstZipInfoStats = result.getMappedResults().get(0);
		assertThat(firstZipInfoStats.get("size"), is((Object) "small"));
		assertThat(firstZipInfoStats.get("population"), is((Object) 6055));
	}

	/**
	 * @see DATAMONGO-861
	 */
	@Test
	public void aggregationUsingIfNullProjection() {

		mongoTemplate.insert(new LineItem("id", "caption", 0));
		mongoTemplate.insert(new LineItem("idonly", null, 0));

		TypedAggregation<LineItem> aggregation = newAggregation(LineItem.class, //
				project("id") //
						.and("caption")//
						.applyCondition(ifNull(field("caption"), "unknown")),
				sort(ASC, "id"));

		assertThat(aggregation.toString(), is(notNullValue()));

		AggregationResults<Document> result = mongoTemplate.aggregate(aggregation, Document.class);
		assertThat(result.getMappedResults().size(), is(2));

		Document id = result.getMappedResults().get(0);
		assertThat((String) id.get("caption"), is(equalTo("caption")));

		Document idonly = result.getMappedResults().get(1);
		assertThat((String) idonly.get("caption"), is(equalTo("unknown")));
	}

	/**
	 * @see DATAMONGO-861
	 */
	@Test
	public void aggregationUsingIfNullReplaceWithFieldReferenceProjection() {

		mongoTemplate.insert(new LineItem("id", "caption", 0));
		mongoTemplate.insert(new LineItem("idonly", null, 0));

		TypedAggregation<LineItem> aggregation = newAggregation(LineItem.class, //
				project("id") //
						.and("caption")//
						.applyCondition(ifNull(field("caption"), field("id"))),
				sort(ASC, "id"));

		assertThat(aggregation.toString(), is(notNullValue()));

		AggregationResults<Document> result = mongoTemplate.aggregate(aggregation, Document.class);
		assertThat(result.getMappedResults().size(), is(2));

		Document id = result.getMappedResults().get(0);
		assertThat((String) id.get("caption"), is(equalTo("caption")));

		Document idonly = result.getMappedResults().get(1);
		assertThat((String) idonly.get("caption"), is(equalTo("idonly")));
	}

	/**
	 * @see DATAMONGO-861
	 */
	@Test
	public void shouldAllowGroupingUsingConditionalExpressions() {

		mongoTemplate.dropCollection(CarPerson.class);

		CarPerson person1 = new CarPerson("first1", "last1", new CarDescriptor.Entry("MAKE1", "MODEL1", 2000),
				new CarDescriptor.Entry("MAKE1", "MODEL2", 2001));

		CarPerson person2 = new CarPerson("first2", "last2", new CarDescriptor.Entry("MAKE3", "MODEL4", 2014));
		CarPerson person3 = new CarPerson("first3", "last3", new CarDescriptor.Entry("MAKE2", "MODEL5", 2015));

		mongoTemplate.save(person1);
		mongoTemplate.save(person2);
		mongoTemplate.save(person3);

		TypedAggregation<CarPerson> agg = Aggregation.newAggregation(CarPerson.class,
				unwind("descriptors.carDescriptor.entries"), //
				project() //
						.and(new ConditionalOperator(Criteria.where("descriptors.carDescriptor.entries.make").is("MAKE1"), "good",
								"meh"))
						.as("make") //
						.and("descriptors.carDescriptor.entries.model").as("model") //
						.and("descriptors.carDescriptor.entries.year").as("year"), //
				group("make").avg(new ConditionalOperator(Criteria.where("year").gte(2012), 1, 9000)).as("score"),
				sort(ASC, "make"));

		AggregationResults<Document> result = mongoTemplate.aggregate(agg, Document.class);

		assertThat(result.getMappedResults(), hasSize(2));

		Document meh = result.getMappedResults().get(0);
		assertThat((String) meh.get("_id"), is(equalTo("meh")));
		assertThat(((Number) meh.get("score")).longValue(), is(equalTo(1L)));

		Document good = result.getMappedResults().get(1);
		assertThat((String) good.get("_id"), is(equalTo("good")));
		assertThat(((Number) good.get("score")).longValue(), is(equalTo(9000L)));
	}

	/**
	 * @see http://docs.mongodb.org/manual/tutorial/aggregation-examples/#return-the-five-most-common-likes
	 */
	@Test
	public void returnFiveMostCommonLikesAggregationFrameworkExample() {

		createUserWithLikesDocuments();

		TypedAggregation<UserWithLikes> agg = createUsersWithCommonLikesAggregation();

		assertThat(agg, is(notNullValue()));
		assertThat(agg.toString(), is(notNullValue()));

		AggregationResults<LikeStats> result = mongoTemplate.aggregate(agg, LikeStats.class);
		assertThat(result, is(notNullValue()));
		assertThat(result.getMappedResults(), is(notNullValue()));
		assertThat(result.getMappedResults().size(), is(5));

		assertLikeStats(result.getMappedResults().get(0), "a", 4);
		assertLikeStats(result.getMappedResults().get(1), "b", 2);
		assertLikeStats(result.getMappedResults().get(2), "c", 4);
		assertLikeStats(result.getMappedResults().get(3), "d", 2);
		assertLikeStats(result.getMappedResults().get(4), "e", 3);
	}

	protected TypedAggregation<UserWithLikes> createUsersWithCommonLikesAggregation() {
		return newAggregation(UserWithLikes.class, //
				unwind("likes"), //
				group("likes").count().as("number"), //
				sort(DESC, "number"), //
				limit(5), //
				sort(ASC, previousOperation()) //
		);
	}

	@Test
	public void arithmenticOperatorsInProjectionExample() {

		Product product = new Product("P1", "A", 1.99, 3, 0.05, 0.19);
		mongoTemplate.insert(product);

		TypedAggregation<Product> agg = newAggregation(Product.class, //
				project("name", "netPrice") //
						.and("netPrice").plus(1).as("netPricePlus1") //
						.and("netPrice").minus(1).as("netPriceMinus1") //
						.and("netPrice").multiply(2).as("netPriceMul2") //
						.and("netPrice").divide(1.19).as("netPriceDiv119") //
						.and("spaceUnits").mod(2).as("spaceUnitsMod2") //
						.and("spaceUnits").plus("spaceUnits").as("spaceUnitsPlusSpaceUnits") //
						.and("spaceUnits").minus("spaceUnits").as("spaceUnitsMinusSpaceUnits") //
						.and("spaceUnits").multiply("spaceUnits").as("spaceUnitsMultiplySpaceUnits") //
						.and("spaceUnits").divide("spaceUnits").as("spaceUnitsDivideSpaceUnits") //
						.and("spaceUnits").mod("spaceUnits").as("spaceUnitsModSpaceUnits") //
		);

		AggregationResults<Document> result = mongoTemplate.aggregate(agg, Document.class);
		List<Document> resultList = result.getMappedResults();

		assertThat(resultList, is(notNullValue()));
		assertThat((String) resultList.get(0).get("_id"), is(product.id));
		assertThat((String) resultList.get(0).get("name"), is(product.name));
		assertThat((Double) resultList.get(0).get("netPricePlus1"), is(product.netPrice + 1));
		assertThat((Double) resultList.get(0).get("netPriceMinus1"), is(product.netPrice - 1));
		assertThat((Double) resultList.get(0).get("netPriceMul2"), is(product.netPrice * 2));
		assertThat((Double) resultList.get(0).get("netPriceDiv119"), is(product.netPrice / 1.19));
		assertThat((Integer) resultList.get(0).get("spaceUnitsMod2"), is(product.spaceUnits % 2));
		assertThat((Integer) resultList.get(0).get("spaceUnitsPlusSpaceUnits"),
				is(product.spaceUnits + product.spaceUnits));
		assertThat((Integer) resultList.get(0).get("spaceUnitsMinusSpaceUnits"),
				is(product.spaceUnits - product.spaceUnits));
		assertThat((Integer) resultList.get(0).get("spaceUnitsMultiplySpaceUnits"),
				is(product.spaceUnits * product.spaceUnits));
		assertThat((Double) resultList.get(0).get("spaceUnitsDivideSpaceUnits"),
				is((double) (product.spaceUnits / product.spaceUnits)));
		assertThat((Integer) resultList.get(0).get("spaceUnitsModSpaceUnits"), is(product.spaceUnits % product.spaceUnits));
	}

	/**
	 * @see DATAMONGO-774
	 */
	@Test
	public void expressionsInProjectionExample() {

		Product product = new Product("P1", "A", 1.99, 3, 0.05, 0.19);
		mongoTemplate.insert(product);

		TypedAggregation<Product> agg = newAggregation(Product.class, //
				project("name", "netPrice") //
						.andExpression("netPrice + 1").as("netPricePlus1") //
						.andExpression("netPrice - 1").as("netPriceMinus1") //
						.andExpression("netPrice / 2").as("netPriceDiv2") //
						.andExpression("netPrice * 1.19").as("grossPrice") //
						.andExpression("spaceUnits % 2").as("spaceUnitsMod2") //
						.andExpression("(netPrice * 0.8  + 1.2) * 1.19").as("grossPriceIncludingDiscountAndCharge") //

		);

		AggregationResults<Document> result = mongoTemplate.aggregate(agg, Document.class);
		List<Document> resultList = result.getMappedResults();

		assertThat(resultList, is(notNullValue()));
		assertThat((String) resultList.get(0).get("_id"), is(product.id));
		assertThat((String) resultList.get(0).get("name"), is(product.name));
		assertThat((Double) resultList.get(0).get("netPricePlus1"), is(product.netPrice + 1));
		assertThat((Double) resultList.get(0).get("netPriceMinus1"), is(product.netPrice - 1));
		assertThat((Double) resultList.get(0).get("netPriceDiv2"), is(product.netPrice / 2));
		assertThat((Double) resultList.get(0).get("grossPrice"), is(product.netPrice * 1.19));
		assertThat((Integer) resultList.get(0).get("spaceUnitsMod2"), is(product.spaceUnits % 2));
		assertThat((Double) resultList.get(0).get("grossPriceIncludingDiscountAndCharge"),
				is((product.netPrice * 0.8 + 1.2) * 1.19));
	}

	/**
	 * @see DATAMONGO-774
	 */
	@Test
	public void stringExpressionsInProjectionExample() {

		assumeTrue(mongoVersion.isGreaterThanOrEqualTo(TWO_DOT_FOUR));

		Product product = new Product("P1", "A", 1.99, 3, 0.05, 0.19);
		mongoTemplate.insert(product);

		TypedAggregation<Product> agg = newAggregation(Product.class, //
				project("name", "netPrice") //
						.andExpression("concat(name, '_bubu')").as("name_bubu") //
		);

		AggregationResults<Document> result = mongoTemplate.aggregate(agg, Document.class);
		List<Document> resultList = result.getMappedResults();

		assertThat(resultList, is(notNullValue()));
		assertThat((String) resultList.get(0).get("_id"), is(product.id));
		assertThat((String) resultList.get(0).get("name"), is(product.name));
		assertThat((String) resultList.get(0).get("name_bubu"), is(product.name + "_bubu"));
	}

	/**
	 * @see DATAMONGO-774
	 */
	@Test
	public void expressionsInProjectionExampleShowcase() {

		Product product = new Product("P1", "A", 1.99, 3, 0.05, 0.19);
		mongoTemplate.insert(product);

		double shippingCosts = 1.2;

		TypedAggregation<Product> agg = newAggregation(Product.class, //
				project("name", "netPrice") //
						.andExpression("(netPrice * (1-discountRate)  + [0]) * (1+taxRate)", shippingCosts).as("salesPrice") //
		);

		AggregationResults<Document> result = mongoTemplate.aggregate(agg, Document.class);
		List<Document> resultList = result.getMappedResults();

		assertThat(resultList, is(notNullValue()));
		Document firstItem = resultList.get(0);
		assertThat((String) firstItem.get("_id"), is(product.id));
		assertThat((String) firstItem.get("name"), is(product.name));
		assertThat((Double) firstItem.get("salesPrice"),
				is((product.netPrice * (1 - product.discountRate) + shippingCosts) * (1 + product.taxRate)));
	}

	@Test
	public void shouldThrowExceptionIfUnknownFieldIsReferencedInArithmenticExpressionsInProjection() {

		exception.expect(MappingException.class);
		exception.expectMessage("unknown");

		Product product = new Product("P1", "A", 1.99, 3, 0.05, 0.19);
		mongoTemplate.insert(product);

		TypedAggregation<Product> agg = newAggregation(Product.class, //
				project("name", "netPrice") //
						.andExpression("unknown + 1").as("netPricePlus1") //
		);

		mongoTemplate.aggregate(agg, Document.class);
	}

	/**
	 * @see DATAMONGO-753
	 * @see http
	 *      ://stackoverflow.com/questions/18653574/spring-data-mongodb-aggregation-framework-invalid-reference-in-group
	 *      -operati
	 */
	@Test
	public void allowsNestedFieldReferencesAsGroupIdsInGroupExpressions() {

		mongoTemplate.insert(new DATAMONGO753().withPDs(new PD("A", 1), new PD("B", 1), new PD("C", 1)));
		mongoTemplate.insert(new DATAMONGO753().withPDs(new PD("B", 1), new PD("B", 1), new PD("C", 1)));

		TypedAggregation<DATAMONGO753> agg = newAggregation(DATAMONGO753.class, //
				unwind("pd"), //
				group("pd.pDch") // the nested field expression
						.sum("pd.up").as("uplift"), //
				project("_id", "uplift"));

		AggregationResults<Document> result = mongoTemplate.aggregate(agg, Document.class);
		List<Document> stats = result.getMappedResults();

		assertThat(stats.size(), is(3));
		assertThat(stats.get(0).get("_id").toString(), is("C"));
		assertThat((Integer) stats.get(0).get("uplift"), is(2));
		assertThat(stats.get(1).get("_id").toString(), is("B"));
		assertThat((Integer) stats.get(1).get("uplift"), is(3));
		assertThat(stats.get(2).get("_id").toString(), is("A"));
		assertThat((Integer) stats.get(2).get("uplift"), is(1));
	}

	/**
	 * @see DATAMONGO-753
	 * @see http
	 *      ://stackoverflow.com/questions/18653574/spring-data-mongodb-aggregation-framework-invalid-reference-in-group
	 *      -operati
	 */
	@Test
	public void aliasesNestedFieldInProjectionImmediately() {

		mongoTemplate.insert(new DATAMONGO753().withPDs(new PD("A", 1), new PD("B", 1), new PD("C", 1)));
		mongoTemplate.insert(new DATAMONGO753().withPDs(new PD("B", 1), new PD("B", 1), new PD("C", 1)));

		TypedAggregation<DATAMONGO753> agg = newAggregation(DATAMONGO753.class, //
				unwind("pd"), //
				project().and("pd.up").as("up"));

		AggregationResults<Document> results = mongoTemplate.aggregate(agg, Document.class);
		List<Document> mappedResults = results.getMappedResults();

		assertThat(mappedResults, hasSize(6));
		for (Document element : mappedResults) {
			assertThat(element.get("up"), is((Object) 1));
		}
	}

	/**
	 * @DATAMONGO-774
	 */
	@Test
	public void shouldPerformDateProjectionOperatorsCorrectly() throws ParseException {

		assumeTrue(mongoVersion.isGreaterThanOrEqualTo(TWO_DOT_FOUR));

		Data data = new Data();
		data.stringValue = "ABC";
		mongoTemplate.insert(data);

		TypedAggregation<Data> agg = newAggregation(Data.class,
				project() //
						.andExpression("concat(stringValue, 'DE')").as("concat") //
						.andExpression("strcasecmp(stringValue,'XYZ')").as("strcasecmp") //
						.andExpression("substr(stringValue,1,1)").as("substr") //
						.andExpression("toLower(stringValue)").as("toLower") //
						.andExpression("toUpper(toLower(stringValue))").as("toUpper") //
		);

		AggregationResults<Document> results = mongoTemplate.aggregate(agg, Document.class);
		Document document = results.getUniqueMappedResult();

		assertThat(document, is(notNullValue()));
		assertThat((String) document.get("concat"), is("ABCDE"));
		assertThat((Integer) document.get("strcasecmp"), is(-1));
		assertThat((String) document.get("substr"), is("B"));
		assertThat((String) document.get("toLower"), is("abc"));
		assertThat((String) document.get("toUpper"), is("ABC"));
	}

	/**
	 * @DATAMONGO-774
	 */
	@Test
	public void shouldPerformStringProjectionOperatorsCorrectly() throws ParseException {

		assumeTrue(mongoVersion.isGreaterThanOrEqualTo(TWO_DOT_FOUR));

		Data data = new Data();
		data.dateValue = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss.SSSZ").parse("29.08.1983 12:34:56.789+0000");
		mongoTemplate.insert(data);

		TypedAggregation<Data> agg = newAggregation(Data.class,
				project() //
						.andExpression("dayOfYear(dateValue)").as("dayOfYear") //
						.andExpression("dayOfMonth(dateValue)").as("dayOfMonth") //
						.andExpression("dayOfWeek(dateValue)").as("dayOfWeek") //
						.andExpression("year(dateValue)").as("year") //
						.andExpression("month(dateValue)").as("month") //
						.andExpression("week(dateValue)").as("week") //
						.andExpression("hour(dateValue)").as("hour") //
						.andExpression("minute(dateValue)").as("minute") //
						.andExpression("second(dateValue)").as("second") //
						.andExpression("millisecond(dateValue)").as("millisecond") //
		);

		AggregationResults<Document> results = mongoTemplate.aggregate(agg, Document.class);
		Document document = results.getUniqueMappedResult();

		assertThat(document, is(notNullValue()));
		assertThat((Integer) document.get("dayOfYear"), is(241));
		assertThat((Integer) document.get("dayOfMonth"), is(29));
		assertThat((Integer) document.get("dayOfWeek"), is(2));
		assertThat((Integer) document.get("year"), is(1983));
		assertThat((Integer) document.get("month"), is(8));
		assertThat((Integer) document.get("week"), is(35));
		assertThat((Integer) document.get("hour"), is(12));
		assertThat((Integer) document.get("minute"), is(34));
		assertThat((Integer) document.get("second"), is(56));
		assertThat((Integer) document.get("millisecond"), is(789));
	}

	/**
	 * @see DATAMONGO-788
	 */
	@Test
	public void referencesToGroupIdsShouldBeRenderedProperly() {

		mongoTemplate.insert(new DATAMONGO788(1, 1));
		mongoTemplate.insert(new DATAMONGO788(1, 1));
		mongoTemplate.insert(new DATAMONGO788(1, 1));
		mongoTemplate.insert(new DATAMONGO788(2, 1));
		mongoTemplate.insert(new DATAMONGO788(2, 1));

		AggregationOperation projectFirst = Aggregation.project("x", "y").and("xField").as("x").and("yField").as("y");
		AggregationOperation group = Aggregation.group("x", "y").count().as("xPerY");
		AggregationOperation project = Aggregation.project("xPerY", "x", "y").andExclude("_id");

		TypedAggregation<DATAMONGO788> aggregation = Aggregation.newAggregation(DATAMONGO788.class, projectFirst, group,
				project);
		AggregationResults<Document> aggResults = mongoTemplate.aggregate(aggregation, Document.class);
		List<Document> items = aggResults.getMappedResults();

		assertThat(items.size(), is(2));
		assertThat((Integer) items.get(0).get("xPerY"), is(2));
		assertThat((Integer) items.get(0).get("x"), is(2));
		assertThat((Integer) items.get(0).get("y"), is(1));
		assertThat((Integer) items.get(1).get("xPerY"), is(3));
		assertThat((Integer) items.get(1).get("x"), is(1));
		assertThat((Integer) items.get(1).get("y"), is(1));
	}

	/**
	 * @see DATAMONGO-806
	 */
	@Test
	public void shouldAllowGroupByIdFields() {

		mongoTemplate.dropCollection(User.class);

		LocalDateTime now = new LocalDateTime();

		User user1 = new User("u1", new PushMessage("1", "aaa", now.toDate()));
		User user2 = new User("u2", new PushMessage("2", "bbb", now.minusDays(2).toDate()));
		User user3 = new User("u3", new PushMessage("3", "ccc", now.minusDays(1).toDate()));

		mongoTemplate.save(user1);
		mongoTemplate.save(user2);
		mongoTemplate.save(user3);

		Aggregation agg = newAggregation( //
				project("id", "msgs"), //
				unwind("msgs"), //
				match(where("msgs.createDate").gt(now.minusDays(1).toDate())), //
				group("id").push("msgs").as("msgs") //
		);

		AggregationResults<Document> results = mongoTemplate.aggregate(agg, User.class, Document.class);

		List<Document> mappedResults = results.getMappedResults();

		Document firstItem = mappedResults.get(0);
		assertThat(firstItem.get("_id"), is(notNullValue()));
		assertThat(String.valueOf(firstItem.get("_id")), is("u1"));
	}

	/**
	 * @see DATAMONGO-840
	 */
	@Test
	public void shouldAggregateOrderDataToAnInvoice() {

		mongoTemplate.dropCollection(Order.class);

		double taxRate = 0.19;

		LineItem product1 = new LineItem("1", "p1", 1.23);
		LineItem product2 = new LineItem("2", "p2", 0.87, 2);
		LineItem product3 = new LineItem("3", "p3", 5.33);

		Order order = new Order("o4711", "c42", new Date()).addItem(product1).addItem(product2).addItem(product3);

		mongoTemplate.save(order);

		AggregationResults<Invoice> results = mongoTemplate.aggregate(newAggregation(Order.class, //
				match(where("id").is(order.getId())), unwind("items"), //
				project("id", "customerId", "items") //
						.andExpression("items.price * items.quantity").as("lineTotal"), //
				group("id") //
						.sum("lineTotal").as("netAmount") //
						.addToSet("items").as("items"), //
				project("id", "items", "netAmount") //
						.and("orderId").previousOperation() //
						.andExpression("netAmount * [0]", taxRate).as("taxAmount") //
						.andExpression("netAmount * (1 + [0])", taxRate).as("totalAmount") //
		), Invoice.class);

		Invoice invoice = results.getUniqueMappedResult();

		assertThat(invoice, is(notNullValue()));
		assertThat(invoice.getOrderId(), is(order.getId()));
		assertThat(invoice.getNetAmount(), is(closeTo(8.3, 000001)));
		assertThat(invoice.getTaxAmount(), is(closeTo(1.577, 000001)));
		assertThat(invoice.getTotalAmount(), is(closeTo(9.877, 000001)));
	}

	/**
	 * @see DATAMONGO-924
	 */
	@Test
	public void shouldAllowGroupingByAliasedFieldDefinedInFormerAggregationStage() {

		mongoTemplate.dropCollection(CarPerson.class);

		CarPerson person1 = new CarPerson("first1", "last1", new CarDescriptor.Entry("MAKE1", "MODEL1", 2000),
				new CarDescriptor.Entry("MAKE1", "MODEL2", 2001), new CarDescriptor.Entry("MAKE2", "MODEL3", 2010),
				new CarDescriptor.Entry("MAKE3", "MODEL4", 2014));

		CarPerson person2 = new CarPerson("first2", "last2", new CarDescriptor.Entry("MAKE3", "MODEL4", 2014));

		CarPerson person3 = new CarPerson("first3", "last3", new CarDescriptor.Entry("MAKE2", "MODEL5", 2011));

		mongoTemplate.save(person1);
		mongoTemplate.save(person2);
		mongoTemplate.save(person3);

		TypedAggregation<CarPerson> agg = Aggregation.newAggregation(CarPerson.class,
				unwind("descriptors.carDescriptor.entries"), //
				project() //
						.and("descriptors.carDescriptor.entries.make").as("make") //
						.and("descriptors.carDescriptor.entries.model").as("model") //
						.and("firstName").as("firstName") //
						.and("lastName").as("lastName"), //
				group("make"));

		AggregationResults<Document> result = mongoTemplate.aggregate(agg, Document.class);

		assertThat(result.getMappedResults(), hasSize(3));
	}

	/**
	 * @see DATAMONGO-960
	 */
	@Test
	public void returnFiveMostCommonLikesAggregationFrameworkExampleWithSortOnDiskOptionEnabled() {

		assumeTrue(mongoVersion.isGreaterThanOrEqualTo(TWO_DOT_SIX));

		createUserWithLikesDocuments();

		TypedAggregation<UserWithLikes> agg = createUsersWithCommonLikesAggregation() //
				.withOptions(newAggregationOptions().allowDiskUse(true).build());

		assertThat(agg, is(notNullValue()));
		assertThat(agg.toString(), is(notNullValue()));

		AggregationResults<LikeStats> result = mongoTemplate.aggregate(agg, LikeStats.class);
		assertThat(result, is(notNullValue()));
		assertThat(result.getMappedResults(), is(notNullValue()));
		assertThat(result.getMappedResults().size(), is(5));

		assertLikeStats(result.getMappedResults().get(0), "a", 4);
		assertLikeStats(result.getMappedResults().get(1), "b", 2);
		assertLikeStats(result.getMappedResults().get(2), "c", 4);
		assertLikeStats(result.getMappedResults().get(3), "d", 2);
		assertLikeStats(result.getMappedResults().get(4), "e", 3);
	}

	/**
	 * @see DATAMONGO-960
	 */
	@Test
	public void returnFiveMostCommonLikesShouldReturnStageExecutionInformationWithExplainOptionEnabled() {

		assumeTrue(mongoVersion.isGreaterThanOrEqualTo(TWO_DOT_SIX));

		createUserWithLikesDocuments();

		TypedAggregation<UserWithLikes> agg = createUsersWithCommonLikesAggregation() //
				.withOptions(newAggregationOptions().explain(true).build());

		AggregationResults<LikeStats> result = mongoTemplate.aggregate(agg, LikeStats.class);

		assertThat(result.getMappedResults(), is(empty()));

		Document rawResult = result.getRawResults();

		assertThat(rawResult, is(notNullValue()));
		assertThat(rawResult.containsKey("stages"), is(true));
	}

	/**
	 * @see DATAMONGO-954
	 */
	@Test
	public void shouldSupportReturningCurrentAggregationRoot() {

		assumeTrue(mongoVersion.isGreaterThanOrEqualTo(TWO_DOT_SIX));

		mongoTemplate.save(new Person("p1_first", "p1_last", 25));
		mongoTemplate.save(new Person("p2_first", "p2_last", 32));
		mongoTemplate.save(new Person("p3_first", "p3_last", 25));
		mongoTemplate.save(new Person("p4_first", "p4_last", 15));

		List<Document> personsWithAge25 = mongoTemplate.find(Query.query(where("age").is(25)), Document.class,
				mongoTemplate.getCollectionName(Person.class));

		Aggregation agg = newAggregation(group("age").push(Aggregation.ROOT).as("users"));
		AggregationResults<Document> result = mongoTemplate.aggregate(agg, Person.class, Document.class);

		assertThat(result.getMappedResults(), hasSize(3));
		Document o = (Document) result.getMappedResults().get(2);

		assertThat(o.get("_id"), is((Object) 25));
		assertThat((List<?>) o.get("users"), hasSize(2));
		assertThat((List<?>) o.get("users"), is(contains(personsWithAge25.toArray())));
	}

	/**
	 * @see DATAMONGO-954
	 * @see http
	 *      ://stackoverflow.com/questions/24185987/using-root-inside-spring-data-mongodb-for-retrieving-whole-document
	 */
	@Test
	public void shouldSupportReturningCurrentAggregationRootInReference() {

		assumeTrue(mongoVersion.isGreaterThanOrEqualTo(TWO_DOT_SIX));

		mongoTemplate.save(new Reservation("0123", "42", 100));
		mongoTemplate.save(new Reservation("0360", "43", 200));
		mongoTemplate.save(new Reservation("0360", "44", 300));

		Aggregation agg = newAggregation( //
				match(where("hotelCode").is("0360")), //
				sort(Direction.DESC, "confirmationNumber", "timestamp"), //
				group("confirmationNumber") //
						.first("timestamp").as("timestamp") //
						.first(Aggregation.ROOT).as("reservationImage") //
		);
		AggregationResults<Document> result = mongoTemplate.aggregate(agg, Reservation.class, Document.class);

		assertThat(result.getMappedResults(), hasSize(2));
	}

	/**
	 * @see DATAMONGO-975
	 */
	@Test
	public void shouldRetrieveDateTimeFragementsCorrectly() throws Exception {

		mongoTemplate.dropCollection(ObjectWithDate.class);

		DateTime dateTime = new DateTime() //
				.withYear(2014) //
				.withMonthOfYear(2) //
				.withDayOfMonth(7) //
				.withTime(3, 4, 5, 6).toDateTime(DateTimeZone.UTC).toDateTimeISO();

		ObjectWithDate owd = new ObjectWithDate(dateTime.toDate());
		mongoTemplate.insert(owd);

		ProjectionOperation dateProjection = Aggregation.project() //
				.and("dateValue").extractHour().as("hour") //
				.and("dateValue").extractMinute().as("min") //
				.and("dateValue").extractSecond().as("second") //
				.and("dateValue").extractMillisecond().as("millis") //
				.and("dateValue").extractYear().as("year") //
				.and("dateValue").extractMonth().as("month") //
				.and("dateValue").extractWeek().as("week") //
				.and("dateValue").extractDayOfYear().as("dayOfYear") //
				.and("dateValue").extractDayOfMonth().as("dayOfMonth") //
				.and("dateValue").extractDayOfWeek().as("dayOfWeek") //
				.andExpression("dateValue + 86400000").extractDayOfYear().as("dayOfYearPlus1Day") //
				.andExpression("dateValue + 86400000").project("dayOfYear").as("dayOfYearPlus1DayManually") //
		;

		Aggregation agg = newAggregation(dateProjection);
		AggregationResults<Document> result = mongoTemplate.aggregate(agg, ObjectWithDate.class, Document.class);

		assertThat(result.getMappedResults(), hasSize(1));
		Document document = result.getMappedResults().get(0);

		assertThat(document.get("hour"), is((Object) dateTime.getHourOfDay()));
		assertThat(document.get("min"), is((Object) dateTime.getMinuteOfHour()));
		assertThat(document.get("second"), is((Object) dateTime.getSecondOfMinute()));
		assertThat(document.get("millis"), is((Object) dateTime.getMillisOfSecond()));
		assertThat(document.get("year"), is((Object) dateTime.getYear()));
		assertThat(document.get("month"), is((Object) dateTime.getMonthOfYear()));
		// dateTime.getWeekOfWeekyear()) returns 6 since for MongoDB the week starts on sunday and not on monday.
		assertThat(document.get("week"), is((Object) 5));
		assertThat(document.get("dayOfYear"), is((Object) dateTime.getDayOfYear()));
		assertThat(document.get("dayOfMonth"), is((Object) dateTime.getDayOfMonth()));

		// dateTime.getDayOfWeek()
		assertThat(document.get("dayOfWeek"), is((Object) 6));
		assertThat(document.get("dayOfYearPlus1Day"), is((Object) dateTime.plusDays(1).getDayOfYear()));
		assertThat(document.get("dayOfYearPlus1DayManually"), is((Object) dateTime.plusDays(1).getDayOfYear()));
	}

	/**
	 * @see DATAMONGO-1127
	 */
	@Test
	public void shouldSupportGeoNearQueriesForAggregationWithDistanceField() {

		mongoTemplate.insert(new Venue("Penn Station", -73.99408, 40.75057));
		mongoTemplate.insert(new Venue("10gen Office", -73.99171, 40.738868));
		mongoTemplate.insert(new Venue("Flatiron Building", -73.988135, 40.741404));

		mongoTemplate.indexOps(Venue.class).ensureIndex(new GeospatialIndex("location"));

		NearQuery geoNear = NearQuery.near(-73, 40, Metrics.KILOMETERS).num(10).maxDistance(150);

		Aggregation agg = newAggregation(Aggregation.geoNear(geoNear, "distance"));
		AggregationResults<Document> result = mongoTemplate.aggregate(agg, Venue.class, Document.class);

		assertThat(result.getMappedResults(), hasSize(3));

		Document firstResult = result.getMappedResults().get(0);
		assertThat(firstResult.containsKey("distance"), is(true));
		assertThat((Double) firstResult.get("distance"), closeTo(117.620092203928, 0.00001));
	}

	/**
	 * @see DATAMONGO-1133
	 */
	@Test
	public void shouldHonorFieldAliasesForFieldReferences() {

		mongoTemplate.insert(new MeterData("m1", "counter1", 42));
		mongoTemplate.insert(new MeterData("m1", "counter1", 13));
		mongoTemplate.insert(new MeterData("m1", "counter1", 45));

		TypedAggregation<MeterData> agg = newAggregation(MeterData.class, //
				match(where("resourceId").is("m1")), //
				group("counterName").sum("counterVolume").as("totalValue"));

		AggregationResults<Document> results = mongoTemplate.aggregate(agg, Document.class);

		assertThat(results.getMappedResults(), hasSize(1));
		Document result = results.getMappedResults().get(0);

		assertThat(result.get("_id"), is(equalTo((Object) "counter1")));
		assertThat(result.get("totalValue"), is(equalTo((Object) 100.0)));
	}

	/**
	 * @see DATAMONGO-1326
	 */
	@Test
	public void shouldLookupPeopleCorectly() {

		assumeTrue(mongoVersion.isGreaterThanOrEqualTo(THREE_DOT_TWO));

		createUsersWithReferencedPersons();

		TypedAggregation<User> agg = newAggregation(User.class, //
				lookup("person", "_id", "firstname", "linkedPerson"), //
				sort(ASC, "id"));

		AggregationResults<Document> results = mongoTemplate.aggregate(agg, User.class, Document.class);

		List<Document> mappedResults = results.getMappedResults();

		Document firstItem = mappedResults.get(0);

		assertThat(firstItem, isBsonObject().containing("_id", "u1"));
		assertThat(firstItem, isBsonObject().containing("linkedPerson.[0].firstname", "u1"));
	}

	/**
	 * @see DATAMONGO-1326
	 */
	@Test
	public void shouldGroupByAndLookupPeopleCorectly() {

		assumeTrue(mongoVersion.isGreaterThanOrEqualTo(THREE_DOT_TWO));

		createUsersWithReferencedPersons();

		TypedAggregation<User> agg = newAggregation(User.class, //
				group().min("id").as("foreignKey"), //
				lookup("person", "foreignKey", "firstname", "linkedPerson"), //
				sort(ASC, "foreignKey", "linkedPerson.firstname"));

		AggregationResults<Document> results = mongoTemplate.aggregate(agg, User.class, Document.class);

		List<Document> mappedResults = results.getMappedResults();

		Document firstItem = mappedResults.get(0);

		assertThat(firstItem, isBsonObject().containing("foreignKey", "u1"));
		assertThat(firstItem, isBsonObject().containing("linkedPerson.[0].firstname", "u1"));
	}

	/**
	 * @see DATAMONGO-1418
	 */
	@Test
	public void shouldCreateOutputCollection() {

		assumeTrue(mongoVersion.isGreaterThanOrEqualTo(TWO_DOT_SIX));

		mongoTemplate.save(new Person("Anna", "Ivanova", 21, Person.Sex.FEMALE));
		mongoTemplate.save(new Person("Pavel", "Sidorov", 36, Person.Sex.MALE));
		mongoTemplate.save(new Person("Anastasia", "Volochkova", 29, Person.Sex.FEMALE));
		mongoTemplate.save(new Person("Igor", "Stepanov", 31, Person.Sex.MALE));
		mongoTemplate.save(new Person("Leoniv", "Yakubov", 55, Person.Sex.MALE));

		String tempOutCollection = "personQueryTemp";
		TypedAggregation<Person> agg = newAggregation(Person.class, //
				group("sex").count().as("count"), //
				sort(DESC, "count"), //
				out(tempOutCollection));

		AggregationResults<Document> results = mongoTemplate.aggregate(agg, Document.class);
		assertThat(results.getMappedResults(), is(empty()));

		List<Document> list = mongoTemplate.findAll(Document.class, tempOutCollection);

		assertThat(list, hasSize(2));
		assertThat(list.get(0), isBsonObject().containing("_id", "MALE").containing("count", 3));
		assertThat(list.get(1), isBsonObject().containing("_id", "FEMALE").containing("count", 2));

		mongoTemplate.dropCollection(tempOutCollection);
	}

	/**
	 * @see DATAMONGO-1418
	 */
	@Test(expected = IllegalArgumentException.class)
	public void outShouldOutBeTheLastOperation() {

		newAggregation(match(new Criteria()), //
				group("field1").count().as("totalCount"), //
				out("collection1"), //
				skip(100L));
	}

	/**
	 * @see DATAMONGO-1457
	 */
	@Test
	public void sliceShouldBeAppliedCorrectly() {

		assumeTrue(mongoVersion.isGreaterThanOrEqualTo(THREE_DOT_TWO));

		createUserWithLikesDocuments();

		TypedAggregation<UserWithLikes> agg = newAggregation(UserWithLikes.class, match(new Criteria()),
				project().and("likes").slice(2));

		AggregationResults<UserWithLikes> result = mongoTemplate.aggregate(agg, UserWithLikes.class);

		assertThat(result.getMappedResults(), hasSize(9));
		for (UserWithLikes user : result) {
			assertThat(user.likes.size() <= 2, is(true));
		}
	}

	private void createUsersWithReferencedPersons() {

		mongoTemplate.dropCollection(User.class);
		mongoTemplate.dropCollection(Person.class);

		User user1 = new User("u1");
		User user2 = new User("u2");
		User user3 = new User("u3");

		mongoTemplate.save(user1);
		mongoTemplate.save(user2);
		mongoTemplate.save(user3);

		Person person1 = new Person("u1", "User 1");
		Person person2 = new Person("u2", "User 2");

		mongoTemplate.save(person1);
		mongoTemplate.save(person2);
		mongoTemplate.save(user3);
	}

	private void assertLikeStats(LikeStats like, String id, long count) {

		assertThat(like, is(notNullValue()));
		assertThat(like.id, is(id));
		assertThat(like.count, is(count));
	}

	private void createUserWithLikesDocuments() {
		mongoTemplate.insert(new UserWithLikes("u1", new Date(), "a", "b", "c"));
		mongoTemplate.insert(new UserWithLikes("u2", new Date(), "a"));
		mongoTemplate.insert(new UserWithLikes("u3", new Date(), "b", "c"));
		mongoTemplate.insert(new UserWithLikes("u4", new Date(), "c", "d", "e"));
		mongoTemplate.insert(new UserWithLikes("u5", new Date(), "a", "e", "c"));
		mongoTemplate.insert(new UserWithLikes("u6", new Date()));
		mongoTemplate.insert(new UserWithLikes("u7", new Date(), "a"));
		mongoTemplate.insert(new UserWithLikes("u8", new Date(), "x", "e"));
		mongoTemplate.insert(new UserWithLikes("u9", new Date(), "y", "d"));
	}

	private void createTagDocuments() {

		MongoCollection<Document> coll = mongoTemplate.getCollection(INPUT_COLLECTION);

		coll.insertOne(createDocument("Doc1", "spring", "mongodb", "nosql"));
		coll.insertOne(createDocument("Doc2", "spring", "mongodb"));
		coll.insertOne(createDocument("Doc3", "spring"));
	}

	private static Document createDocument(String title, String... tags) {

		Document doc = new Document("title", title);
		List<String> tagList = new ArrayList<String>();

		for (String tag : tags) {
			tagList.add(tag);
		}

		doc.put("tags", tagList);
		return doc;
	}

	private static void assertTagCount(String tag, int n, TagCount tagCount) {

		assertThat(tagCount.getTag(), is(tag));
		assertThat(tagCount.getN(), is(n));
	}

	static class DATAMONGO753 {
		PD[] pd;

		DATAMONGO753 withPDs(PD... pds) {
			this.pd = pds;
			return this;
		}
	}

	static class PD {
		String pDch;
		@org.springframework.data.mongodb.core.mapping.Field("alias") int up;

		public PD(String pDch, int up) {
			this.pDch = pDch;
			this.up = up;
		}
	}

	static class DATAMONGO788 {

		int x;
		int y;
		int xField;
		int yField;

		public DATAMONGO788() {}

		public DATAMONGO788(int x, int y) {
			this.x = x;
			this.xField = x;
			this.y = y;
			this.yField = y;
		}
	}

	/**
	 * @see DATAMONGO-806
	 */
	static class User {

		@Id String id;
		List<PushMessage> msgs;

		public User() {}

		public User(String id, PushMessage... msgs) {
			this.id = id;
			this.msgs = Arrays.asList(msgs);
		}
	}

	/**
	 * @see DATAMONGO-806
	 */
	static class PushMessage {

		@Id String id;
		String content;
		Date createDate;

		public PushMessage() {}

		public PushMessage(String id, String content, Date createDate) {
			this.id = id;
			this.content = content;
			this.createDate = createDate;
		}
	}

	@org.springframework.data.mongodb.core.mapping.Document
	static class CarPerson {

		@Id private String id;
		private String firstName;
		private String lastName;
		private Descriptors descriptors;

		public CarPerson(String firstname, String lastname, Entry... entries) {
			this.firstName = firstname;
			this.lastName = lastname;

			this.descriptors = new Descriptors();

			this.descriptors.carDescriptor = new CarDescriptor(entries);
		}
	}

	@SuppressWarnings("unused")
	static class Descriptors {
		private CarDescriptor carDescriptor;
	}

	static class CarDescriptor {

		private List<Entry> entries = new ArrayList<AggregationTests.CarDescriptor.Entry>();

		public CarDescriptor(Entry... entries) {

			for (Entry entry : entries) {
				this.entries.add(entry);
			}
		}

		@SuppressWarnings("unused")
		static class Entry {
			private String make;
			private String model;
			private int year;

			public Entry() {}

			public Entry(String make, String model, int year) {
				this.make = make;
				this.model = model;
				this.year = year;
			}
		}
	}

	static class Reservation {

		String hotelCode;
		String confirmationNumber;
		int timestamp;

		public Reservation() {}

		public Reservation(String hotelCode, String confirmationNumber, int timestamp) {
			this.hotelCode = hotelCode;
			this.confirmationNumber = confirmationNumber;
			this.timestamp = timestamp;
		}
	}

	static class ObjectWithDate {

		Date dateValue;

		public ObjectWithDate(Date dateValue) {
			this.dateValue = dateValue;
		}
	}

	/**
	 * @see DATAMONGO-861
	 */
	@org.springframework.data.mongodb.core.mapping.Document(collection = "inventory")
	static class InventoryItem {

		int id;
		String item;
		String description;
		int qty;

		public InventoryItem() {}

		public InventoryItem(int id, String item, int qty) {

			this.id = id;
			this.item = item;
			this.qty = qty;
		}

		public InventoryItem(int id, String item, String description, int qty) {

			this.id = id;
			this.item = item;
			this.description = description;
			this.qty = qty;
		}
	}
}
