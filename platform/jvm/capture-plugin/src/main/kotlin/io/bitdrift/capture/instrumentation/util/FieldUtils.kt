// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

/*
 * Adapted from https://github.com/apache/commons-lang/blob/ebcb39a62fc1e47251eceaf63a4b3d731c5227a0/src/main/java/org/apache/commons/lang3/reflect/FieldUtils.java
 *
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

package io.bitdrift.capture.instrumentation.util

import java.lang.reflect.Field

/**
 * Gets all fields of the given class and its parents (if any).
 */
internal val Class<*>.allFields: List<Field>
    get() {
        val allFields = mutableListOf<Field>()
        var currentClass: Class<*>? = this
        while (currentClass != null) {
            val declaredFields = currentClass.declaredFields
            allFields += declaredFields
            currentClass = currentClass.superclass
        }
        return allFields
    }
