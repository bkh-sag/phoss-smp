/**
 * Copyright (C) 2019-2021 Philip Helger and contributors
 * philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.phoss.smp.backend.mongodb.mgr;

import java.time.LocalDateTime;
import java.util.Date;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.helger.commons.ValueEnforcer;
import com.helger.commons.annotation.Nonempty;
import com.helger.commons.annotation.ReturnsMutableCopy;
import com.helger.commons.collection.impl.CommonsArrayList;
import com.helger.commons.collection.impl.ICommonsList;
import com.helger.commons.state.EChange;
import com.helger.commons.typeconvert.TypeConverter;
import com.helger.peppolid.IParticipantIdentifier;
import com.helger.phoss.smp.domain.pmigration.EParticipantMigrationDirection;
import com.helger.phoss.smp.domain.pmigration.EParticipantMigrationState;
import com.helger.phoss.smp.domain.pmigration.ISMPParticipantMigration;
import com.helger.phoss.smp.domain.pmigration.ISMPParticipantMigrationManager;
import com.helger.phoss.smp.domain.pmigration.SMPParticipantMigration;
import com.helger.photon.audit.AuditHelper;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;

/**
 * Implementation of {@link ISMPParticipantMigrationManager} for MongoDB
 *
 * @author Philip Helger
 * @since 5.4.0
 */
public final class SMPParticipantMigrationManagerMongoDB extends AbstractManagerMongoDB implements
                                                         ISMPParticipantMigrationManager
{
  private static final String BSON_ID = "id";
  private static final String BSON_DIRECTION = "direction";
  private static final String BSON_STATE = "state";
  private static final String BSON_PARTICIPANT_ID = "pid";
  private static final String BSON_INIT_DT = "initdt";
  private static final String BSON_MIGRATION_KEY = "migkey";

  public SMPParticipantMigrationManagerMongoDB ()
  {
    super ("smp-participant-migration");
    getCollection ().createIndex (Indexes.ascending (BSON_ID));
  }

  @Nonnull
  @ReturnsMutableCopy
  public static Document toBson (@Nonnull final ISMPParticipantMigration aValue)
  {
    return new Document ().append (BSON_ID, aValue.getID ())
                          .append (BSON_DIRECTION, aValue.getDirection ().getID ())
                          .append (BSON_STATE, aValue.getState ().getID ())
                          .append (BSON_PARTICIPANT_ID, toBson (aValue.getParticipantIdentifier ()))
                          .append (BSON_INIT_DT, TypeConverter.convert (aValue.getInitiationDateTime (), Date.class))
                          .append (BSON_MIGRATION_KEY, aValue.getMigrationKey ());
  }

  @Nonnull
  @ReturnsMutableCopy
  public static SMPParticipantMigration toDomain (@Nonnull final Document aDoc)
  {
    final String sID = aDoc.getString (BSON_ID);
    final EParticipantMigrationDirection eDirection = EParticipantMigrationDirection.getFromIDOrNull (aDoc.getString (BSON_DIRECTION));
    final EParticipantMigrationState eState = EParticipantMigrationState.getFromIDOrNull (aDoc.getString (BSON_STATE));
    final IParticipantIdentifier aParticipantID = toParticipantID (aDoc.get (BSON_PARTICIPANT_ID, Document.class));
    final LocalDateTime aInitiationDateTime = TypeConverter.convert (aDoc.getDate (BSON_INIT_DT), LocalDateTime.class);
    final String sMigrationKey = aDoc.getString (BSON_MIGRATION_KEY);
    return new SMPParticipantMigration (sID, eDirection, eState, aParticipantID, aInitiationDateTime, sMigrationKey);
  }

  private void _createParticipantMigration (@Nonnull final SMPParticipantMigration aSMPParticipantMigration)
  {
    ValueEnforcer.notNull (aSMPParticipantMigration, "SMPParticipantMigration");
    getCollection ().insertOne (toBson (aSMPParticipantMigration));

    AuditHelper.onAuditCreateSuccess (SMPParticipantMigration.OT,
                                      aSMPParticipantMigration.getID (),
                                      aSMPParticipantMigration.getDirection (),
                                      aSMPParticipantMigration.getParticipantIdentifier ().getURIEncoded (),
                                      aSMPParticipantMigration.getInitiationDateTime (),
                                      aSMPParticipantMigration.getMigrationKey ());
  }

  @Nonnull
  public ISMPParticipantMigration createOutboundParticipantMigration (@Nonnull final IParticipantIdentifier aParticipantID,
                                                                      @Nonnull @Nonempty final String sMigrationKey)
  {
    final SMPParticipantMigration aSMPParticipantMigration = SMPParticipantMigration.createOutbound (aParticipantID,
                                                                                                     sMigrationKey);
    _createParticipantMigration (aSMPParticipantMigration);
    return aSMPParticipantMigration;
  }

  @Nonnull
  public EChange setParticipantMigrationState (@Nullable final String sParticipantMigrationID,
                                               @Nonnull final EParticipantMigrationState eNewState)
  {
    final SMPParticipantMigration aPM = getParticipantMigrationOfID (sParticipantMigrationID);
    if (aPM == null)
    {
      AuditHelper.onAuditModifyFailure (SMPParticipantMigration.OT, sParticipantMigrationID, "no-such-id");
      return EChange.UNCHANGED;
    }

    {
      EChange eChange = EChange.UNCHANGED;
      eChange = eChange.or (aPM.setState (eNewState));
      if (eChange.isUnchanged ())
        return EChange.UNCHANGED;

      getCollection ().findOneAndReplace (new Document (BSON_ID, sParticipantMigrationID), toBson (aPM));
    }
    AuditHelper.onAuditModifySuccess (SMPParticipantMigration.OT,
                                      "migration-state",
                                      sParticipantMigrationID,
                                      eNewState);
    return EChange.CHANGED;
  }

  @Nullable
  public SMPParticipantMigration getParticipantMigrationOfID (@Nullable final String sID)
  {
    final Document aMatch = getCollection ().find (new Document (BSON_ID, sID)).first ();
    if (aMatch == null)
      return null;
    return toDomain (aMatch);
  }

  @Nonnull
  @ReturnsMutableCopy
  public ICommonsList <ISMPParticipantMigration> getAllOutboundParticipantMigrations (@Nullable final EParticipantMigrationState eState)
  {
    Bson aFilter = new Document (BSON_DIRECTION, EParticipantMigrationDirection.OUTBOUND.getID ());
    if (eState != null)
      aFilter = Filters.and (aFilter, new Document (BSON_STATE, eState.getID ()));

    final ICommonsList <ISMPParticipantMigration> ret = new CommonsArrayList <> ();
    getCollection ().find (aFilter).forEach (x -> ret.add (toDomain (x)));
    return ret;
  }

  @Nonnull
  @ReturnsMutableCopy
  public ICommonsList <ISMPParticipantMigration> getAllInboundParticipantMigrations (@Nullable final EParticipantMigrationState eState)
  {
    Bson aFilter = new Document (BSON_DIRECTION, EParticipantMigrationDirection.INBOUND.getID ());
    if (eState != null)
      aFilter = Filters.and (aFilter, new Document (BSON_STATE, eState.getID ()));

    final ICommonsList <ISMPParticipantMigration> ret = new CommonsArrayList <> ();
    getCollection ().find (aFilter).forEach (x -> ret.add (toDomain (x)));
    return ret;
  }

  public boolean containsOutboundMigrationInProgress (@Nullable final IParticipantIdentifier aParticipantID)
  {
    if (aParticipantID == null)
      return false;

    return getCollection ().find (Filters.and (new Document (BSON_DIRECTION,
                                                             EParticipantMigrationDirection.OUTBOUND.getID ()),
                                               new Document (BSON_PARTICIPANT_ID, toBson (aParticipantID))))
                           .iterator ()
                           .hasNext ();
  }

  public boolean containsInboundMigrationInProgress (@Nullable final IParticipantIdentifier aParticipantID)
  {
    if (aParticipantID == null)
      return false;

    return getCollection ().find (Filters.and (new Document (BSON_DIRECTION,
                                                             EParticipantMigrationDirection.INBOUND.getID ()),
                                               new Document (BSON_PARTICIPANT_ID, toBson (aParticipantID))))
                           .iterator ()
                           .hasNext ();
  }
}
