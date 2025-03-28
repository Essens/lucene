/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.lucene.analysis.synonym.word2vec;

import static org.apache.lucene.util.hnsw.HnswGraphBuilder.DEFAULT_BEAM_WIDTH;
import static org.apache.lucene.util.hnsw.HnswGraphBuilder.DEFAULT_MAX_CONN;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import org.apache.lucene.index.VectorEncoding;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.TermAndBoost;
import org.apache.lucene.util.hnsw.HnswGraph;
import org.apache.lucene.util.hnsw.HnswGraphBuilder;
import org.apache.lucene.util.hnsw.HnswGraphSearcher;
import org.apache.lucene.util.hnsw.NeighborQueue;

/**
 * The Word2VecSynonymProvider generates the list of sysnonyms of a term.
 *
 * @lucene.experimental
 */
public class Word2VecSynonymProvider {

  private static final VectorSimilarityFunction SIMILARITY_FUNCTION =
      VectorSimilarityFunction.DOT_PRODUCT;
  private static final VectorEncoding VECTOR_ENCODING = VectorEncoding.FLOAT32;
  private final Word2VecModel word2VecModel;
  private final HnswGraph hnswGraph;

  /**
   * Word2VecSynonymProvider constructor
   *
   * @param model containing the set of TermAndVector entries
   */
  public Word2VecSynonymProvider(Word2VecModel model) throws IOException {
    word2VecModel = model;

    HnswGraphBuilder<float[]> builder =
        HnswGraphBuilder.create(
            word2VecModel,
            VECTOR_ENCODING,
            SIMILARITY_FUNCTION,
            DEFAULT_MAX_CONN,
            DEFAULT_BEAM_WIDTH,
            HnswGraphBuilder.randSeed);
    this.hnswGraph = builder.build(word2VecModel.copy());
  }

  public List<TermAndBoost> getSynonyms(
      BytesRef term, int maxSynonymsPerTerm, float minAcceptedSimilarity) throws IOException {

    if (term == null) {
      throw new IllegalArgumentException("Term must not be null");
    }

    LinkedList<TermAndBoost> result = new LinkedList<>();
    float[] query = word2VecModel.vectorValue(term);
    if (query != null) {
      NeighborQueue synonyms =
          HnswGraphSearcher.search(
              query,
              // The query vector is in the model. When looking for the top-k
              // it's always the nearest neighbour of itself so, we look for the top-k+1
              maxSynonymsPerTerm + 1,
              word2VecModel,
              VECTOR_ENCODING,
              SIMILARITY_FUNCTION,
              hnswGraph,
              null,
              word2VecModel.size());

      int size = synonyms.size();
      for (int i = 0; i < size; i++) {
        float similarity = synonyms.topScore();
        int id = synonyms.pop();

        BytesRef synonym = word2VecModel.termValue(id);
        // We remove the original query term
        if (!synonym.equals(term) && similarity >= minAcceptedSimilarity) {
          result.addFirst(new TermAndBoost(synonym, similarity));
        }
      }
    }
    return result;
  }
}
