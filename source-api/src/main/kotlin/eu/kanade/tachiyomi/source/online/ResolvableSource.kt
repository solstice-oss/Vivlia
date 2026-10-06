/*
 * Copyright 2015 Tachiyomi Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License me.
 */

package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga

/**
 * A source that may handle opening an SManga or SChapter for a given URI.
 *
 * @since extensions-lib 1.5
 */
interface ResolvableSource : Source {

    /**
     * Returns what the given URI may open.
     * Returns [UriType.Unknown] if the source is not able to resolve the URI.
     *
     * @since extensions-lib 1.5
     */
    fun getUriType(uri: String): UriType

    /**
     * Called if [getUriType] is [UriType.Manga].
     * Returns the corresponding SManga, if possible.
     *
     * @since extensions-lib 1.5
     */
    suspend fun getManga(uri: String): SManga?

    /**
     * Called if [getUriType] is [UriType.Chapter].
     * Returns the corresponding SChapter, if possible.
     *
     * @since extensions-lib 1.5
     */
    suspend fun getChapter(uri: String): SChapter?
}

sealed interface UriType {
    data object Manga : UriType
    data object Chapter : UriType
    data object Unknown : UriType
}
