/*
 *  Copyright (c) 2015-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.models;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BaseService.AttributeNameStringValue;
import org.twinlife.twinlife.BaseService.AttributeNameValue;
import org.twinlife.twinlife.BaseService.AttributeNameVoidValue;

import java.util.List;
import java.util.UUID;

//
// version: 1.2
//

public class PairProtocol {

    //
    // Twincode Attributes
    //

    private static final String TWINCODE_ATTRIBUTE_META_PAIR = "meta::pair::";

    private static final String TWINCODE_ATTRIBUTE_PAIR = "pair::";
    private static final String TWINCODE_ATTRIBUTE_PAIR_TWINCODE_OUTBOUND_ID = "pair::twincodeOutboundId";

    //
    // Invoke Actions & Attributes
    //
    public static final String PARAM_TWINCODE_OUTBOUND_ID = "twincodeOutboundId";

    public static final String ACTION_PAIR_BIND = "pair::bind";
    private static final String INVOKE_TWINCODE_ACTION_PAIR_BIND_ATTRIBUTE_TWINCODE_OUTBOUND_ID = "twincodeOutboundId";
    public static final String ACTION_PAIR_INVITE = "pair::invite";
    private static final String INVOKE_TWINCODE_ACTION_PAIR_INVITE_ATTRIBUTE_TWINCODE_OUTBOUND_ID = "twincodeOutboundId";
    public static final String ACTION_PAIR_UNBIND = "pair::unbind";
    public static final String ACTION_PAIR_REFRESH = "pair::refresh";

    /*
     * <pre>
     *
     * Pair Protocol
     *
     *
     *
     * pair::invite -- pair::bind
     *
     *
     *         Peer1                                                      Peer2
     *
     *    profileA
     *    publicIdentityId = identityIdA
     *
     *   identityA
     *    id = identityIdA
     *    twincodeFactoryId = twincodeFactoryIdA
     *    twincodeInboundId = twincodeInboundIdA
     *    twincodeOutboundId = twincodeOutboundIdA
     *    twincodeSwitchId = twincodeSwitchIdA
     *
     *   twincodeFactoryA
     *    id = twincodeFactoryIdA
     *    twincodeInboundId = twincodeInboundIdA
     *    twincodeOutboundId = twincodeOutboundIdA
     *    twincodeSwitchId = twincodeSwitchIdA
     *    attributes:
     *     meta::pair::
     *
     *   twincodeInboundA
     *    id = twincodeInboundIdA
     *    attributes:
     *     meta::pair::
     *
     *   twincodeOutboundA
     *    id = twincodeOutboundIdA
     *    attributes:
     *     meta::pair::
     *
     *   twincodeSwitchA
     *    id = twincodeSwitchIdA
     *    twincodeInboundId = twincodeInboundIdA
     *    twincodeOutboundId = twincodeOutboundIdA
     *    attributes:
     *     meta::pair::
     *
     *
     *                                                                    Peer2 (Anonymous)
     *
     *
     *
     *                                                    ImportInvitationActivity (Anonymous)
     *                                                     => CreateContactPhase1Executor
     *
     *                                                      contactB
     *                                                       id = contactIdB
     *                                                       publicPeerTwincodeOutboundId = twincodeOutboundIdA
     *                                                       privatePeerTwincodeOutboundId = null
     *                                                       privateIdentityId = null
     *
     *
     *
     *                                                                    Peer2 (name)
     *
     *
     *                                                    ImportInvitationActivity (name)
     *                                                     => CreateContactPhase1Executor
     *
     *                                                      contactB
     *                                                       id = contactIdB
     *                                                       publicPeerTwincodeOutboundId = twincodeOutboundIdA
     *                                                       privatePeerTwincodeOutboundId = null
     *                                                       privateIdentityId = privateIdentityIdB
     *
     *                                                      privateIdentityB
     *                                                       id = privateIdentityIdB
     *                                                       twincodeFactoryId = twincodeFactoryIdB
     *                                                       twincodeInboundId = twincodeInboundIdB
     *                                                       twincodeOutboundId = twincodeOutboundIdB
     *                                                       twincodeSwitchId = twincodeSwitchIdB
     *
     *                                                      twincodeFactoryB
     *                                                       id = twincodeFactoryIdB
     *                                                       twincodeInboundId = twincodeInboundIdB
     *                                                       twincodeOutboundId = twincodeOutboundIdB
     *                                                       twincodeSwitchId = twincodeSwitchIdB
     *                                                       attributes:
     *                                                        pair::
     *
     *                                                      twincodeInboundB
     *                                                       id = twincodeInboundIdB
     *                                                       attributes:
     *                                                        pair::
     *
     *                                                      twincodeOutboundB
     *                                                       id = twincodeOutboundIdB
     *                                                       attributes:
     *                                                        pair::
     *
     *                                                      twincodeSwitchB
     *                                                       id = twincodeSwitchIdB
     *                                                       twincodeInboundId = twincodeInboundIdB
     *                                                       twincodeOutboundId = twincodeOutboundIdB
     *                                                       attributes:
     *                                                        pair::
     *
     *                                                    invoke
     *                                                     id = twincodeOutboundIdA
     *                                                     action = pair::invite
     *                                                     attributes:
     *                                                      twincodeOutboundId = twincodeOutboundIdB
     *
     * onInvoke
     *  twincodeInboundId = twincodeInboundIdA
     *  action = pair::invite
     *  attributes:
     *   twincodeOutboundId = twincodeOutboundIdB
     *
     *
     *         Peer1 (name)
     *
     *
     *  => CreateContactPhase2Executor
     *
     *  contactC
     *   id = contactIdB
     *   publicPeerTwincodeOutboundId = null
     *   privatePeerTwincodeOutboundId = twincodeOutboundIdB
     *   privateIdentityId = privateIdentityIdC
     *
     *  privateIdentityC
     *   id = privateIdentityIdC
     *   twincodeFactoryId = twincodeFactoryIdC
     *   twincodeInboundId = twincodeInboundIdC
     *   twincodeOutboundId = twincodeOutboundIdC
     *   twincodeSwitchId = twincodeSwitchIdC
     *
     *  twincodeFactoryC
     *   id = twincodeFactoryIdC
     *   twincodeInboundId = twincodeInboundIdC
     *   twincodeOutboundId = twincodeOutboundIdC
     *   twincodeSwitchId = twincodeSwitchIdC
     *   attributes:
     *    pair::
     *
     *  twincodeInboundC
     *   id = twincodeInboundIdC
     *   attributes:
     *    pair::
     *    pair::twincodeOutboundId = twincodeOutboundIdB
     *
     *  twincodeOutboundC
     *   id = twincodeOutboundIdC
     *   attributes:
     *    pair::
     *
     *  twincodeSwitchC
     *   id = twincodeSwitchIdC
     *   twincodeInboundId = twincodeInboundIdC
     *   twincodeOutboundId = twincodeOutboundIdC
     *   attributes:
     *    pair::
     *
     * invoke
     *  id = twincodeOutboundIdB
     *  action = pair::bind
     *  attributes:
     *   twincodeOutboundId = twincodeOutboundIdC
     *
     *                                                    onInvoke
     *                                                     twincodeInboundId = twincodeInboundIdB
     *                                                     action = pair::bind
     *                                                     attributes:
     *                                                      twincodeOutboundId = twincodeOutboundIdC
     *
     *                                                     => BindContactExecutor
     *
     *                                                      contactB
     *                                                       id = contactIdB
     *                                                       publicPeerTwincodeOutboundId = twincodeOutboundIdA
     *                                                       privatePeerTwincodeOutboundId = twincodeOutboundIdC
     *                                                       privateIdentityId = privateIdentityIdB
     *
     *                                                      privateIdentityB
     *                                                       id = privateIdentityIdB
     *                                                       twincodeFactoryId = twincodeFactoryIdB
     *                                                       twincodeInboundId = twincodeInboundIdB
     *                                                       twincodeOutboundId = twincodeOutboundIdB
     *                                                       twincodeSwitchId = twincodeSwitchIdB
     *
     *                                                      twincodeFactoryB
     *                                                       id = twincodeFactoryIdB
     *                                                       twincodeInboundId = twincodeInboundIdB
     *                                                       twincodeOutboundId = twincodeOutboundIdB
     *                                                       twincodeSwitchId = twincodeSwitchIdB
     *                                                       attributes:
     *                                                        pair::
     *
     *                                                      twincodeInboundB
     *                                                       id = twincodeInboundIdB
     *                                                       attributes:
     *                                                        pair::
     *                                                        pair::twincodeOutboundId = twincodeOutboundIdC
     *
     *                                                      twincodeOutboundB
     *                                                       attributes:
     *                                                        pair::
     *
     *                                                      twincodeSwitchB
     *                                                       twincodeInboundId = twincodeInboundIdB
     *                                                       twincodeOutboundId = twincodeOutboundIdB
     *                                                       attributes:
     *                                                        pair::
     *
     *
     *
     * pair::unbind
     *
     * invoke
     *  id = twincodeOutboundIdB
     *  action = pair::unbind
     *  attributes:
     *
     *                                                    onInvoke
     *                                                     twincodeInboundId = twincodeInboundIdB
     *                                                     action = pair::unbind
     *                                                     attributes:
     *
     *
     *
     * pair::refresh
     *
     * invoke
     *  id = twincodeOutboundIdB
     *  action = pair::refresh
     *  attributes:
     *
     *                                                    onInvoke
     *                                                     twincodeInboundId = twincodeInboundIdB
     *                                                     action = pair::refresh
     *                                                     attributes:
     *
     *
     * </pre>
     */

    //
    // Twincode Attributes
    //
    public static void setTwincodeAttributeMetaPair(@NonNull List<AttributeNameValue> attributes) {

        attributes.add(new AttributeNameVoidValue(TWINCODE_ATTRIBUTE_META_PAIR));
    }

    public static void setTwincodeAttributePair(@NonNull List<AttributeNameValue> attributes) {

        attributes.add(new AttributeNameVoidValue(TWINCODE_ATTRIBUTE_PAIR));
    }

    public static void setTwincodeAttributePairTwincodeId(@NonNull List<AttributeNameValue> attributes, @NonNull UUID twincodeId) {

        attributes.add(new AttributeNameStringValue(TWINCODE_ATTRIBUTE_PAIR_TWINCODE_OUTBOUND_ID, twincodeId.toString()));
    }

    public static void setInvokeTwincodeActionPairBindAttributeTwincodeId(@NonNull List<AttributeNameValue> attributes, @NonNull UUID twincodeId) {

        attributes.add(new AttributeNameStringValue(INVOKE_TWINCODE_ACTION_PAIR_BIND_ATTRIBUTE_TWINCODE_OUTBOUND_ID, twincodeId.toString()));
    }

    @NonNull
    public static String invokeTwincodeActionPairBindAttributeTwincodeId() {

        return INVOKE_TWINCODE_ACTION_PAIR_BIND_ATTRIBUTE_TWINCODE_OUTBOUND_ID;
    }

    public static void setInvokeTwincodeActionPairInviteAttributeTwincodeId(@NonNull List<AttributeNameValue> attributes, @NonNull UUID twincodeId) {

        attributes.add(new AttributeNameStringValue(INVOKE_TWINCODE_ACTION_PAIR_INVITE_ATTRIBUTE_TWINCODE_OUTBOUND_ID, twincodeId.toString()));
    }

    @NonNull
    public static String invokeTwincodeActionPairInviteAttributeTwincodeId() {

        return INVOKE_TWINCODE_ACTION_PAIR_INVITE_ATTRIBUTE_TWINCODE_OUTBOUND_ID;
    }
}
