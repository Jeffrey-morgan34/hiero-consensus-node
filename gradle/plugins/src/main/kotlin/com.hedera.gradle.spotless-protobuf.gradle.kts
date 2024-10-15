/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

plugins { id("com.hedera.gradle.spotless") }

spotless {
    protobuf {
        //        buf().pathToExe("/usr/local/bin/buf-1.24.0")
        buf("1.45.0").pathToExe("/opt/homebrew/bin/buf")
        target("**/*.proto") // target every '.proto'
        //        custom("Buf Lint") { "/usr/local/bin/buf lint" }
        licenseHeader("/* (C) MAYBE NEXT YEAR */") // or licenseHeaderFile
    }
}
