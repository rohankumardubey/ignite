/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// ReSharper disable MemberCanBePrivate.Local
// ReSharper disable UnusedAutoPropertyAccessor.Local
// ReSharper disable UnusedMember.Local
namespace Apache.Ignite.Core.Tests.Cache.Query
{
    using System;
    using System.Linq;
    using Apache.Ignite.Core.Binary;
    using Apache.Ignite.Core.Cache.Configuration;
    using Apache.Ignite.Core.Cache.Query;
    using Apache.Ignite.Core.Impl.Binary;
    using NUnit.Framework;

    /// <summary>
    /// Tests queries with in-code configuration.
    /// </summary>
    public class CacheQueriesCodeConfigurationTest
    {
        const string CacheName = "personCache";

        /// <summary>
        /// Tears down the test fixture.
        /// </summary>
        [TearDown]
        public void TearDown()
        {
            Ignition.StopAll(true);
        }

        /// <summary>
        /// Tests the SQL query.
        /// </summary>
        [Test]
        public void TestQueryEntityConfiguration()
        {
            var cfg = new IgniteConfiguration(TestUtils.GetTestConfiguration())
            {
                BinaryConfiguration = new BinaryConfiguration(typeof(QueryPerson)),
                CacheConfiguration = new[]
                {
                    new CacheConfiguration(CacheName, new QueryEntity(typeof(int), typeof(QueryPerson))
                    {
                        TableName = "CustomTableName",
                        Fields = new[]
                        {
                            new QueryField("Name", typeof(string)),
                            new QueryField("Age", typeof(int)),
                            new QueryField("Birthday", typeof(DateTime)),
                        },
                        Indexes = new[]
                        {
                            new QueryIndex
                            {
                                InlineSize = 2048,
                                IndexType = QueryIndexType.FullText,
                                Fields = new[]
                                {
                                    new QueryIndexField
                                    {
                                        IsDescending = false,
                                        Name = "Name"
                                    }
                                }

                            },
                            new QueryIndex("Age")
                        }
                    })
                }
            };

            using (var ignite = Ignition.Start(cfg))
            {
                var cache = ignite.GetCache<int, QueryPerson>(CacheName);

                Assert.IsNotNull(cache);

                cache[1] = new QueryPerson("Arnold", 10);
                cache[2] = new QueryPerson("John", 20);

#pragma warning disable 618
                using (var cursor = cache.Query(new SqlQuery(typeof (QueryPerson), "age > ? and birthday < ?",
#pragma warning restore 618
                    10, DateTime.UtcNow)))
                {
                    Assert.AreEqual(2, cursor.GetAll().Single().Key);
                }

                using (var cursor = cache.Query(new SqlFieldsQuery(
                    "select _key from CustomTableName where age > ? and birthday < ?", 10, DateTime.UtcNow)))
                {
                    Assert.AreEqual(2, cursor.GetAll().Single()[0]);
                }

                using (var cursor = cache.Query(new TextQuery(typeof (QueryPerson), "Ar*")))
                {
                    Assert.AreEqual(1, cursor.GetAll().Single().Key);
                }
            }
        }

        /// <summary>
        /// Tests the attribute configuration.
        /// </summary>
        [Test]
        public void TestAttributeConfiguration()
        {
            // ReSharper disable once ObjectCreationAsStatement
            Assert.Throws<InvalidOperationException>(() => new QueryEntity(typeof (RecursiveQuery)));

            var qe = new QueryEntity {ValueType = typeof(AttributeTest) };

            Assert.AreEqual(typeof(AttributeTest), qe.ValueType);

            var fields = qe.Fields.ToArray();
            var idxField = fields.Single(x => x.Name == "IndexedField1");

            CollectionAssert.AreEquivalent(new[]
            {
                "SqlField", "IndexedField1", "FullTextField", "Inner", "Inner.Foo",
                "GroupIndex1", "GroupIndex2", "GroupIndex3"
            }, fields.Select(x => x.Name));

            Assert.IsTrue(fields.Single(x => x.Name == "SqlField").NotNull);
            Assert.IsFalse(idxField.NotNull);

            var idx = qe.Indexes.ToArray();

            Assert.AreEqual(QueryIndexType.Sorted, idx[0].IndexType);
            Assert.AreEqual(QueryIndexType.Sorted, idx[1].IndexType);
            Assert.AreEqual(QueryIndexType.Sorted, idx[2].IndexType);
            Assert.AreEqual(QueryIndexType.FullText, idx[3].IndexType);

            CollectionAssert.AreEquivalent(new[] {"GroupIndex1", "GroupIndex2"}, idx[0].Fields.Select(f => f.Name));
            CollectionAssert.AreEquivalent(new[] {"GroupIndex1", "GroupIndex3"}, idx[1].Fields.Select(f => f.Name));
            CollectionAssert.AreEquivalent(new[] {"IndexedField1"}, idx[2].Fields.Select(f => f.Name));
            CollectionAssert.AreEquivalent(new[] {"FullTextField"}, idx[3].Fields.Select(f => f.Name));

            Assert.AreEqual(-1, idx[0].InlineSize);
            Assert.AreEqual(-1, idx[1].InlineSize);
            Assert.AreEqual(513, idx[2].InlineSize);
            Assert.AreEqual(-1, idx[3].InlineSize);

            Assert.AreEqual(3, idxField.Precision);
            Assert.AreEqual(4, idxField.Scale);
        }

        /// <summary>
        /// Tests the attribute configuration query.
        /// </summary>
        [Test]
        public void TestAttributeConfigurationQuery()
        {
            var cfg = new IgniteConfiguration(TestUtils.GetTestConfiguration())
            {
                BinaryConfiguration = new BinaryConfiguration(
                    typeof (AttributeQueryPerson), typeof (AttributeQueryAddress))
            };

            using (var ignite = Ignition.Start(cfg))
            {
                var cache = ignite.GetOrCreateCache<int, AttributeQueryPerson>(new CacheConfiguration(CacheName,
                        new QueryEntity(typeof(int), typeof(AttributeQueryPerson))));

                Assert.IsNotNull(cache);

                cache[1] = new AttributeQueryPerson("Arnold", 10)
                {
                    Address = new AttributeQueryAddress {Country = "USA", Street = "Pine Tree road", Zip = 1}
                };

                cache[2] = new AttributeQueryPerson("John", 20);

#pragma warning disable 618
                using (var cursor = cache.Query(new SqlQuery(typeof(AttributeQueryPerson),
                    "age > ? and age < ? and birthday > ? and birthday < ?", 10, 30,
                    DateTime.UtcNow.AddYears(-21), DateTime.UtcNow.AddYears(-19))))
                {
                    Assert.AreEqual(2, cursor.GetAll().Single().Key);
                }

                using (var cursor = cache.Query(new SqlQuery(typeof(AttributeQueryPerson), "salary > ?", 10)))
                {
                    Assert.AreEqual(2, cursor.GetAll().Single().Key);
                }

                using (var cursor = cache.Query(new SqlQuery(typeof(AttributeQueryPerson), "Country = ?", "USA")))
                {
                    Assert.AreEqual(1, cursor.GetAll().Single().Key);
                }

                using (var cursor = cache.Query(new SqlQuery(typeof(AttributeQueryPerson), "Zip = ?", 1)))
                {
                    Assert.AreEqual(1, cursor.GetAll().Single().Key);
                }
#pragma warning restore 618

                using (var cursor = cache.Query(new TextQuery(typeof(AttributeQueryPerson), "Ar*")))
                {
                    Assert.AreEqual(1, cursor.GetAll().Single().Key);
                }

                using (var cursor = cache.Query(new TextQuery(typeof(AttributeQueryPerson), "Pin*")))
                {
                    Assert.AreEqual(1, cursor.GetAll().Single().Key);
                }
            }
        }

        /// <summary>
        /// Tests query entity validation when no <see cref="QuerySqlFieldAttribute"/> has been set.
        /// </summary>
        [Test]
        public void TestQueryEntityValidationWithMissingQueryAttributes()
        {
            using (var ignite = Ignition.Start(TestUtils.GetTestConfiguration()))
            {
                var cfg = new CacheConfiguration(
                    TestUtils.TestName,
                    new QueryEntity(typeof(string), typeof(MissingAttributesTest)));

                var cache = ignite.GetOrCreateCache<string, MissingAttributesTest>(cfg);

                cache["1"] = new MissingAttributesTest {Foo = "Bar"};
            }
        }

        /// <summary>
        /// Tests that key and value types can be generic.
        /// </summary>
        [Test]
        public void TestGenericQueryTypes()
        {
            var ignite = Ignition.Start(TestUtils.GetTestConfiguration());

            var cfg = new CacheConfiguration(TestUtils.TestName)
            {
                QueryEntities = new[] {new QueryEntity(typeof(GenericTest<int>), typeof(GenericTest2<string>))}
            };

            var cache = ignite.GetOrCreateCache<GenericTest<int>, GenericTest2<string>>(cfg);
            var key = new GenericTest<int>(1);
            var value = new GenericTest2<string>("foo");
            cache[key] = value;

            var binType = ignite.GetBinary().GetBinaryType(value.GetType());
            var expectedTypeName = BinaryBasicNameMapper.FullNameInstance.GetTypeName(value.GetType().FullName);
            var expectedTypeId = BinaryUtils.GetStringHashCodeLowerCase(expectedTypeName);

            Assert.AreEqual(expectedTypeName, binType.TypeName);
            Assert.AreEqual(expectedTypeId, binType.TypeId);

            var queryEntity = cache.GetConfiguration().QueryEntities.Single();
            Assert.AreEqual(expectedTypeName, queryEntity.ValueTypeName);

            var sqlRes = cache.Query(new SqlFieldsQuery("SELECT Foo, Bar from GENERICTEST2")).Single();

            Assert.AreEqual(key.Foo, sqlRes[0]);
            Assert.AreEqual(value.Bar, sqlRes[1]);
        }

        /// <summary>
        /// Tests that query types can be nested generic.
        /// </summary>
        [Test]
        public void TestNestedGenericQueryTypes()
        {
            var ignite = Ignition.Start(TestUtils.GetTestConfiguration());

            var cfg = new CacheConfiguration(TestUtils.TestName)
            {
                QueryEntities = new[] {new QueryEntity(typeof(int), typeof(GenericTest<GenericTest2<string>>))}
            };

            var cache = ignite.GetOrCreateCache<int, GenericTest<GenericTest2<string>>>(cfg);
            var value = new GenericTest<GenericTest2<string>>(new GenericTest2<string>("foobar"));
            cache[1] = value;

            var sqlRes = cache.Query(new SqlFieldsQuery("SELECT Bar from GENERICTEST")).Single().Single();

            Assert.AreEqual(value.Foo.Bar, sqlRes);

            var valTypeName = value.GetType().FullName;
            Assert.IsNotNull(valTypeName);
        }

        /// <summary>
        /// Class without any <see cref="QuerySqlFieldAttribute"/> attributes.
        /// </summary>
        private class MissingAttributesTest
        {
            /** */
            public string Foo { get; set; }
        }

        /// <summary>
        /// Test person.
        /// </summary>
        private class AttributeQueryPerson
        {
            /// <summary>
            /// Initializes a new instance of the <see cref="AttributeQueryPerson"/> class.
            /// </summary>
            /// <param name="name">The name.</param>
            /// <param name="age">The age.</param>
            public AttributeQueryPerson(string name, int age)
            {
                Name = name;
                Age = age;
                Salary = age;
                Birthday = DateTime.UtcNow.AddYears(-age);
            }

            /// <summary>
            /// Gets or sets the name.
            /// </summary>
            /// <value>
            /// The name.
            /// </value>
            [QueryTextField(Name = "Name")]
            public string Name { get; set; }

            /// <summary>
            /// Gets or sets the age.
            /// </summary>
            /// <value>
            /// The age.
            /// </value>
            [QuerySqlField]
            public int Age { get; set; }

            /// <summary>
            /// Gets or sets the address.
            /// </summary>
            /// <value>
            /// The address.
            /// </value>
            [QuerySqlField]
            public AttributeQueryAddress Address { get; set; }

            /// <summary>
            /// Gets or sets the salary.
            /// </summary>
            [QuerySqlField]
            public decimal? Salary { get; set; }

            /// <summary>
            /// Gets or sets the birthday.
            /// </summary>
            [QuerySqlField]
            public DateTime Birthday { get; set; }
        }

        /// <summary>
        /// Address.
        /// </summary>
        private class AttributeQueryAddress
        {
            /// <summary>
            /// Gets or sets the country.
            /// </summary>
            /// <value>
            /// The country.
            /// </value>
            [QuerySqlField]
            public string Country { get; set; }

            /// <summary>
            /// Gets or sets the zip.
            /// </summary>
            /// <value>
            /// The zip.
            /// </value>
            [QuerySqlField(IsIndexed = true)]
            public int Zip { get; set; }

            /// <summary>
            /// Gets or sets the street.
            /// </summary>
            /// <value>
            /// The street.
            /// </value>
            [QueryTextField]
            public string Street { get; set; }
        }

        /// <summary>
        /// Query.
        /// </summary>
        private class RecursiveQuery
        {
            /// <summary>
            /// Gets or sets the inner.
            /// </summary>
            /// <value>
            /// The inner.
            /// </value>
            [QuerySqlField]
            public RecursiveQuery Inner { get; set; }
        }

        /// <summary>
        /// Attribute test class.
        /// </summary>
        private class AttributeTest
        {
            [QuerySqlField(NotNull = true)]
            public double SqlField { get; set; }

            [QuerySqlField(IsIndexed = true, Name = "IndexedField1", IsDescending = true, IndexInlineSize = 513,
                DefaultValue = 42, Precision = 3, Scale = 4)]
            public int IndexedField { get; set; }

            [QueryTextField]
            public string FullTextField { get; set; }

            [QuerySqlField]
            public AttributeTestInner Inner { get; set; }

            [QuerySqlField(IsIndexed = true, IndexGroups = new[] {"group1", "group2"})]
            public string GroupIndex1 { get; set; }

            [QuerySqlField(IsIndexed = true, IndexGroups = new[] {"group1"})]
            public string GroupIndex2 { get; set; }

            [QuerySqlField(IsIndexed = true, IndexGroups = new[] {"group2"})]
            public string GroupIndex3 { get; set; }
        }

        /// <summary>
        /// Inner class.
        /// </summary>
        private class AttributeTestInner
        {
            /// <summary>
            /// Gets or sets the foo.
            /// </summary>
            /// <value>
            /// The foo.
            /// </value>
            [QuerySqlField]
            public string Foo { get; set; }
        }

        /// <summary>
        /// Generic query type.
        /// </summary>
        private class GenericTest<T>
        {
            /** */
            public GenericTest(T foo)
            {
                Foo = foo;
            }

            /** */
            [QuerySqlField]
            public T Foo { get; set; }
        }

        /// <summary>
        /// Generic query type.
        /// </summary>
        private class GenericTest2<T>
        {
            /** */
            public GenericTest2(T bar)
            {
                Bar = bar;
            }

            /** */
            [QuerySqlField]
            public T Bar { get; set; }
        }
    }
}
