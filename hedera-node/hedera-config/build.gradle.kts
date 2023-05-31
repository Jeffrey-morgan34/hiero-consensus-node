/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

plugins {
  id("com.hedera.hashgraph.conventions")
  `java-test-fixtures`
}

description = "Hedera Configuration"

dependencies {
  implementation(project(":hedera-node:hedera-mono-service"))
  implementation(project(":hedera-node:hapi-utils"))
  implementation(libs.swirlds.config)
  compileOnlyApi(libs.spotbugs.annotations)
  implementation(libs.pbj.runtime)

  testImplementation(testLibs.bundles.testing)

  testFixturesImplementation(libs.swirlds.test.framework)
  testFixturesCompileOnlyApi(libs.spotbugs.annotations)
}
