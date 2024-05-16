#!/usr/bin/env bash

# The location were this script can be found.
SCRIPT_PATH="$(dirname "$(readlink -f "$0")")"

# You must install mermaid to use this script.
# npm install -g @mermaid-js/mermaid-cli

# Add the flag "--less-mystery" to add back labels for mystery input wires (noisy diagram warning)

pcli diagram \
    -l 'TransactionPrehandler:futures:consensusRoundHandler' \
    -l 'EventCreationManager:get transactions:TransactionPool' \
    -l 'RunningEventHasher:future hash:consensusRoundHandler' \
    -l 'ConsensusEventStream:future hash:consensusRoundHandler' \
    -s 'eventWindowManager:event window:🌀' \
    -s 'heartbeat:heartbeat:❤️' \
    -s 'TransactionPrehandler:futures:🔮' \
    -s 'pcesReplayer:done streaming pces:✅' \
    -s 'OrphanBufferSplitter:events to gossip:📬' \
    -s 'getKeystoneEventSequenceNumber:flush request:🚽' \
    -s 'extractOldestMinimumGenerationOnDisk:minimum identifier to store:📀' \
    -s 'StaleEventDetectorRouter:non-validated events:🍎' \
    -s 'Mystery Input:mystery data:❔' \
    -s 'stateSigner:submit transaction:🖋️' \
    -s 'stateSigner:signature transactions:🖋️' \
    -s 'IssDetectorSplitter:IssNotification:💥' \
    -s 'getStatusAction:PlatformStatusAction:💀' \
    -s 'toNotification:state written notification:📦' \
    -s 'latestCompleteStateNotifier:complete state notification:💢' \
    -s 'OrphanBufferSplitter:preconsensus signatures:🔰' \
    -s 'RunningEventHashOverride:hash override:💨' \
    -s 'TransactionResubmitterSplitter:submit transaction:♻️' \
    -s 'StaleEventDetectorRouter:publishStaleEvent:⚰️' \
    -s 'toStateWrittenToDiskAction:PlatformStatusAction:💾' \
    -s 'StatusStateMachine:PlatformStatus:🚦' \
    -g 'Event Validation:InternalEventValidator,EventDeduplicator,EventSignatureValidator' \
    -g 'Event Hashing:EventHasher,PostHashCollector' \
    -g 'Orphan Buffer:OrphanBuffer,OrphanBufferSplitter' \
    -g 'Consensus Engine:ConsensusEngine,ConsensusEngineSplitter,eventWindowManager,getKeystoneEventSequenceNumber,getConsensusEvents' \
    -g 'State File Manager:saveToDiskFilter,signedStateFileManager,extractOldestMinimumGenerationOnDisk,toStateWrittenToDiskAction,toNotification' \
    -g 'State File Management:State File Manager,📦,📀,💾' \
    -g 'State Signature Collector:StateSignatureCollector,reservedStateSplitter,allStatesReserver,completeStateFilter,completeStatesReserver,extractConsensusSignatureTransactions,extractPreconsensusSignatureTransactions,latestCompleteStateNotifier' \
    -g 'State Signature Collection:State Signature Collector,latestCompleteStateNexus,💢' \
    -g 'Preconsensus Event Stream:PcesSequencer,PcesWriter' \
    -g 'Event Creation:EventCreationManager,TransactionPool,SelfEventSigner' \
    -g 'ISS Detector:IssDetector,IssDetectorSplitter,issHandler,getStatusAction' \
    -g 'Heartbeat:heartbeat,❤️' \
    -g 'PCES Replay:pcesReplayer,✅' \
    -g 'Consensus Round Handler:consensusRoundHandler,postHandler_stateAndRoundReserver,getState,savedStateController' \
    -g 'State Hasher:StateHasher,postHasher_stateAndRoundReserver,postHasher_getConsensusRound,postHasher_stateReserver' \
    -g 'Consensus:Consensus Engine,🚽,🌀' \
    -g 'State Verification:stateSigner,hashLogger,ISS Detector,🖋️,💥,💀' \
    -g 'Transaction Handling:Consensus Round Handler,latestImmutableStateNexus' \
    -g 'Round Durability Buffer:RoundDurabilityBuffer,RoundDurabilityBufferSplitter' \
    -g 'Stale Event Detector:StaleEventDetector,StaleEventDetectorSplitter,StaleEventDetectorRouter' \
    -g 'Transaction Resubmitter:TransactionResubmitter,TransactionResubmitterSplitter' \
    -g 'Stale Events:Stale Event Detector,Transaction Resubmitter,⚰️,♻️,🍎' \
    -c 'Orphan Buffer' \
    -c 'Consensus Engine' \
    -c 'State Signature Collector' \
    -c 'State File Manager' \
    -c 'Consensus Round Handler' \
    -c 'State Hasher' \
    -c 'ISS Detector' \
    -c 'Round Durability Buffer' \
    -c 'Wait For Crash Durability' \
    -c 'Stale Event Detector' \
    -c 'Transaction Resubmitter' \
    -o "${SCRIPT_PATH}/../../../../../../../../docs/core/wiring-diagram.svg"
