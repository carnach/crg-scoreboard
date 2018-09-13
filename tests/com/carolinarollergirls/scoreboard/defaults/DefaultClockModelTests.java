package com.carolinarollergirls.scoreboard.defaults;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.LinkedList;
import java.util.Queue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.carolinarollergirls.scoreboard.Clock;
import com.carolinarollergirls.scoreboard.Ruleset;
import com.carolinarollergirls.scoreboard.Settings;
import com.carolinarollergirls.scoreboard.event.AsyncScoreBoardListener;
import com.carolinarollergirls.scoreboard.event.ConditionalScoreBoardListener;
import com.carolinarollergirls.scoreboard.event.ScoreBoardEvent;
import com.carolinarollergirls.scoreboard.event.ScoreBoardListener;
import com.carolinarollergirls.scoreboard.model.ClockModel;
import com.carolinarollergirls.scoreboard.model.ScoreBoardModel;

public class DefaultClockModelTests {

	private ScoreBoardModel sbModelMock;
	private Ruleset ruleMock;
	private Settings settingsMock;
	
	private Queue<ScoreBoardEvent> collectedEvents;
	public ScoreBoardListener listener = new ScoreBoardListener() {
		
		@Override
		public void scoreBoardChange(ScoreBoardEvent event) {
			synchronized(collectedEvents) {
				collectedEvents.add(event);
			}
		}
	};
	
	
	private DefaultClockModel clock;
	private static String ID = "TEST";
	
	private boolean syncStatus = false;
	
	private void advance(long time_ms) {
	    DefaultClockModel.updateClockTimerTask.advance(time_ms);
	    AsyncScoreBoardListener.waitForEvents();
	}
	
	@Before
	public void setUp() throws Exception {
		syncStatus = false;
		collectedEvents = new LinkedList<ScoreBoardEvent>();
		
		sbModelMock = Mockito.mock(DefaultScoreBoardModel.class);
		
		ruleMock = Mockito.mock(Ruleset.class);
		settingsMock = Mockito.mock(Settings.class);
		
		Mockito
			.when(sbModelMock.getScoreBoard())
			.thenReturn(sbModelMock);
		
		Mockito
			.when(sbModelMock._getRuleset())
			.thenReturn(ruleMock);
		
		Mockito
			.when(sbModelMock.getSettings())
			.thenReturn(settingsMock);
		
		// makes it easier to test both sync and non-sync paths through clock model
		Mockito
			.when(settingsMock.getBoolean("ScoreBoard.Clock.Sync"))
			.thenAnswer(new Answer<Boolean>() {
				public Boolean answer(InvocationOnMock invocation) throws Throwable {
					return syncStatus;
				}
			});
		
		clock = new DefaultClockModel(sbModelMock, ID);
		DefaultClockModel.updateClockTimerTask.setPaused(true);
	}
	
	@After
	public void tearDown() throws Exception {
		DefaultClockModel.updateClockTimerTask.setPaused(false);
	}

	@Test
	public void testDefaults() {
		assertEquals(0, clock.getMinimumNumber());
		assertEquals(0, clock.getMaximumNumber());
		assertEquals(0, clock.getNumber());
		
		assertEquals(ID, clock.getId());
		assertEquals(null, clock.getName());
		assertFalse(clock.isMasterClock());
		assertFalse(clock.isCountDirectionDown());
		assertFalse(clock.isRunning());

		assertEquals(clock, clock.getClock());
		assertEquals(sbModelMock, clock.getScoreBoard());
		assertEquals(sbModelMock, clock.getScoreBoardModel());
		
		assertEquals("Clock", clock.getProviderName());
		assertEquals(ID, clock.getProviderId());
		assertEquals(Clock.class, clock.getProviderClass());
	}

	@Test
	public void testReset() {
		clock.setMaximumNumber(5);
		clock.setMinimumNumber(2);
		clock.setNumber(4);
		clock.setMaximumTime(1200000);
		clock.setTime(5000);
		clock.setCountDirectionDown(true);
		
		clock.reset();
		
		assertTrue(clock.isCountDirectionDown());
		assertEquals(clock.getMinimumNumber(), clock.getNumber());
		assertTrue(clock.isTimeAtStart());
	}
	
	@Test
	public void testRestoreSnapshot() {
		clock.setMaximumNumber(5);
		clock.setNumber(4);
		clock.setMaximumTime(1200000);
		clock.setTime(5000);
		clock.start();
		ClockModel.ClockSnapshotModel snapshot = clock.snapshot();
		
		clock.reset();
		assertFalse(clock.isRunning());
		assertEquals(0, clock.getNumber());
		assertEquals(0, clock.getTime());
		
		//of IDs don't match no restore should be done
		clock.id = "OTHER";
		clock.restoreSnapshot(snapshot);
		assertFalse(clock.isRunning());
		assertEquals(0, clock.getNumber());
		assertEquals(0, clock.getTime());
		
		clock.id = "TEST";
		clock.restoreSnapshot(snapshot);
		assertTrue(clock.isRunning());
		assertEquals(4, clock.getNumber());
		assertEquals(5000, clock.getTime());		
	}
	
	@Test
	public void testSetting_ClockSync() {
		//add a master clock
		DefaultClockModel clock2 = new DefaultClockModel(sbModelMock, Clock.ID_TIMEOUT);
		syncStatus = true;
		clock.setMaximumTime(10000);
		clock2.setMaximumTime(10000);
		clock2.setTime(3400);
		assertEquals(3400, clock2.getTime());
		
		//no syncing is done if the clock is stopped
		clock.setTime(4200);
		assertEquals(4200, clock.getTime());

		//when the clocks are started the non-master clock is synced to the master clock
		clock2.start();
		clock.start();
		assertEquals(4400, clock.getTime());

		//changes under 1s are ignored. Even if multiple changes accumulate to more than 1s
		clock.changeTime(500);
		clock.changeTime(800);
		assertEquals(4400, clock.getTime());

		//changes over 1s are rounded down
		clock.changeTime(1100);
		assertEquals(5400, clock.getTime());

		//the previous statements also apply to the master clock
		clock2.changeTime(500);
		clock2.changeTime(800);
		assertEquals(3400, clock2.getTime());
		clock2.changeTime(1000);
		assertEquals(4400, clock2.getTime());

		//advancing the time affects both clocks even if less than 1s
		advance(400);
		assertEquals(4800, clock2.getTime());
		assertEquals(5800, clock.getTime());
	}
	
	@Test
	public void testSetName() {
		clock.addScoreBoardListener(new ConditionalScoreBoardListener(clock, Clock.EVENT_NAME, listener));
		
		clock.setName("Test Clock");
		advance(0);
		
		assertEquals("Test Clock", clock.getName());
		assertEquals(1, collectedEvents.size());
		ScoreBoardEvent event = collectedEvents.poll();
		assertEquals("Test Clock", event.getValue());
		assertEquals(null, event.getPreviousValue());
	}
	
	public void testSetCountDirectionDown() {
		assertFalse(clock.isCountDirectionDown());
		clock.addScoreBoardListener(new ConditionalScoreBoardListener(clock, Clock.EVENT_DIRECTION, listener));
		
		clock.setCountDirectionDown(true);
		advance(0);
		assertTrue(clock.isCountDirectionDown());
		assertEquals(1, collectedEvents.size());
		ScoreBoardEvent event = collectedEvents.poll();
		assertTrue((Boolean)event.getValue());
		assertFalse((Boolean)event.getPreviousValue());

		//check idempotency
		clock.setCountDirectionDown(true);
		advance(0);
		assertTrue(clock.isCountDirectionDown());
		assertEquals(1, collectedEvents.size());
		
		clock.setCountDirectionDown(false);
		advance(0);
		assertFalse(clock.isCountDirectionDown());
	}
	
	@Test
	public void testSetMinimumNumber() {
		clock.addScoreBoardListener(new ConditionalScoreBoardListener(clock, Clock.EVENT_MINIMUM_NUMBER, listener));
		
		clock.setMinimumNumber(1);
		advance(0);
		
		// validate constraint: max >= number >= min
		assertEquals(1, clock.getMinimumNumber());
		assertEquals(1, clock.getMaximumNumber());
		assertEquals(1, clock.getNumber());

		assertEquals(1, collectedEvents.size());
		ScoreBoardEvent event = collectedEvents.poll();
		assertEquals(1, event.getValue());
		assertEquals(0, event.getPreviousValue());
	}
	
	@Test
	public void testSetMinimumNumber2() {
		clock.addScoreBoardListener(new ConditionalScoreBoardListener(clock, Clock.EVENT_MINIMUM_NUMBER, listener));
		clock.addScoreBoardListener(new ConditionalScoreBoardListener(clock, Clock.EVENT_MAXIMUM_NUMBER, listener));
		clock.addScoreBoardListener(new ConditionalScoreBoardListener(clock, Clock.EVENT_NUMBER, listener));

		
		clock.setMinimumNumber(10);
		advance(0);
		collectedEvents.clear();

		clock.setMinimumNumber(5);
		advance(0);
		
		// validate constraint: number is automatically set to max
		assertEquals(5, clock.getMinimumNumber());
		assertEquals(10, clock.getMaximumNumber());
		assertEquals(10, clock.getNumber());

		assertEquals(1, collectedEvents.size());
	}
	
	@Test
	public void testSetMaximumNumber() {
		clock.addScoreBoardListener(new ConditionalScoreBoardListener(clock, Clock.EVENT_MAXIMUM_NUMBER, listener));
		
		clock.setMaximumNumber(5);
		advance(0);
		
		assertEquals(0, clock.getMinimumNumber());
		assertEquals(5, clock.getMaximumNumber());
		assertEquals(0, clock.getNumber());

		assertEquals(1, collectedEvents.size());
		ScoreBoardEvent event = collectedEvents.poll();
		assertEquals(5, event.getValue());
		assertEquals(0, event.getPreviousValue());
	}
	
	@Test
	public void testSetMaximumNumber2() {
		clock.addScoreBoardListener(new ConditionalScoreBoardListener(clock, Clock.EVENT_MINIMUM_NUMBER, listener));
		clock.addScoreBoardListener(new ConditionalScoreBoardListener(clock, Clock.EVENT_MAXIMUM_NUMBER, listener));
		clock.addScoreBoardListener(new ConditionalScoreBoardListener(clock, Clock.EVENT_NUMBER, listener));

		
		clock.setMinimumNumber(10);
		advance(0);
		collectedEvents.clear();

		clock.setMaximumNumber(5);
		advance(0);
		
		// validate constraint: cannot set a max that is < min
		assertEquals(10, clock.getMinimumNumber());
		assertEquals(10, clock.getMaximumNumber());
		assertEquals(10, clock.getNumber());

		assertEquals(1, collectedEvents.size());
	}
	
	@Test
	public void testChangeMaximumNumber() {
		clock.addScoreBoardListener(new ConditionalScoreBoardListener(clock, Clock.EVENT_MAXIMUM_NUMBER, listener));
		
		clock.setMaximumNumber(5);
		advance(0);
		collectedEvents.clear();

		clock.changeMaximumNumber(2);
		advance(0);
		
		assertEquals(0, clock.getMinimumNumber());
		assertEquals(7, clock.getMaximumNumber());
		assertEquals(0, clock.getNumber());

		assertEquals(1, collectedEvents.size());
		ScoreBoardEvent event = collectedEvents.poll();
		assertEquals(7, event.getValue());
		assertEquals(5, event.getPreviousValue());
	}
	
	@Test
	public void testChangeMinimumNumber() {
		clock.addScoreBoardListener(new ConditionalScoreBoardListener(clock, Clock.EVENT_MINIMUM_NUMBER, listener));
		
		clock.setMinimumNumber(5);
		advance(0);
		collectedEvents.clear();

		clock.changeMinimumNumber(2);
		advance(0);
		
		assertEquals(7, clock.getMinimumNumber());
		assertEquals(7, clock.getMaximumNumber());
		assertEquals(7, clock.getNumber());

		assertEquals(1, collectedEvents.size());
		ScoreBoardEvent event = collectedEvents.poll();
		assertEquals(7, event.getValue());
		assertEquals(5, event.getPreviousValue());
	}
	
	@Test
	public void testChangeNumber() {
		clock.addScoreBoardListener(new ConditionalScoreBoardListener(clock, Clock.EVENT_NUMBER, listener));
		
		clock.setMaximumNumber(12);
		clock.setMinimumNumber(3);
		advance(0);
		collectedEvents.clear();
		
		clock.setNumber(5);
		advance(0);
		assertEquals(5, clock.getNumber());
		assertEquals(1, collectedEvents.size());
		ScoreBoardEvent event = collectedEvents.poll();
		assertEquals(5, event.getValue());
		assertEquals(3, event.getPreviousValue());
		
		clock.changeNumber(3);
		advance(0);
		assertEquals(8, clock.getNumber());
		assertEquals(1, collectedEvents.size());
		collectedEvents.clear();
	
		// validate constraint: cannot set number above maximum
		clock.setNumber(23);
		advance(0);
		assertEquals(12, clock.getNumber());
		assertEquals(1, collectedEvents.size());
		collectedEvents.clear();
		
		// validate constraint: cannot set number below minimum
		clock.setNumber(-2);
		advance(0);
		assertEquals(3, clock.getNumber());
		assertEquals(1, collectedEvents.size());
		collectedEvents.clear();
		
		// ...and check that constraint is not a >0 type constraint
		clock.setNumber(1);
		advance(0);
		assertEquals(3, clock.getNumber());
		assertEquals(1, collectedEvents.size());
		collectedEvents.clear();
		
		clock.changeNumber(6);
		advance(0);
		assertEquals(9, clock.getNumber());
		assertEquals(1, collectedEvents.size());
		collectedEvents.clear();
		
		// validate constraint: cannot changeNumber above maximum
		clock.changeNumber(6);
		advance(0);
		assertEquals(12, clock.getNumber());
		assertEquals(1, collectedEvents.size());
		collectedEvents.clear();
		
		
		clock.setNumber(5);
		clock.changeNumber(-1);
		advance(0);
		assertEquals(4, clock.getNumber());
		assertEquals(2, collectedEvents.size());
		collectedEvents.clear();
		
		// validate constraint: cannot changeNumber below minimum
		clock.changeNumber(-4);
		advance(0);
		assertEquals(3, clock.getNumber());
		assertEquals(1, collectedEvents.size());
	}
	
	@Test
	public void testSetMinimumTime() {
		clock.addScoreBoardListener(new ConditionalScoreBoardListener(clock, Clock.EVENT_MINIMUM_TIME, listener));
		clock.addScoreBoardListener(new ConditionalScoreBoardListener(clock, Clock.EVENT_MAXIMUM_TIME, listener));
		clock.addScoreBoardListener(new ConditionalScoreBoardListener(clock, Clock.EVENT_TIME, listener));
		
		clock.setMinimumTime(1000);
		advance(0);
		
		// validate constraint: max > min
		assertEquals(1000, clock.getMinimumTime());
		assertEquals(1000, clock.getMaximumTime());
		assertEquals(1000, clock.getTime());

		assertEquals(3, collectedEvents.size());
		ScoreBoardEvent event = collectedEvents.poll();
		assertEquals(1000, (long)event.getValue());
		assertEquals(0, (long)event.getPreviousValue());
	}
	
	@Test
	public void testSetMinimumTime2() {
		clock.addScoreBoardListener(new ConditionalScoreBoardListener(clock, Clock.EVENT_MINIMUM_TIME, listener));

		
		clock.setMinimumTime(2000);
		clock.setMinimumTime(1000);
		advance(0);
		
		// validate constraint: reducing min time doesn't reset max or current time
		assertEquals(1000, clock.getMinimumTime());
		assertEquals(2000, clock.getMaximumTime());
		assertEquals(2000, clock.getTime());
	}
	
	@Test
	public void testSetMinimumTime3() {
		clock.setMaximumTime(2000);
		clock.setMinimumTime(1000);
		
		// validate constraint: time cannot be less than min time
		assertEquals(1000, clock.getMinimumTime());
		assertEquals(2000, clock.getMaximumTime());
		assertEquals(1000, clock.getTime());

	}
	
	@Test
	public void testSetMaximumTime() {
		clock.addScoreBoardListener(new ConditionalScoreBoardListener(clock, Clock.EVENT_MAXIMUM_TIME, listener));
		
		clock.setMaximumTime(5000);
		advance(0);
		
		// validate constraint: increase max time doesn't reset min or current time
		assertEquals(0, clock.getMinimumTime());
		assertEquals(5000, clock.getMaximumTime());
		assertEquals(0, clock.getTime());

		assertEquals(1, collectedEvents.size());
		ScoreBoardEvent event = collectedEvents.poll();
		assertEquals(5000, (long)event.getValue());
		assertEquals(0, (long)event.getPreviousValue());
	}
	
	@Test
	public void testSetMaximumTime2() {
		clock.setMinimumTime(2000);
		clock.setMaximumTime(1000);
		
		// validate constraint: cannot set a max that is < min
		assertEquals(2000, clock.getMinimumTime());
		assertEquals(2000, clock.getMaximumTime());
		assertEquals(2000, clock.getTime());
	} 
	
	@Test
	public void testChangeMaximumTime() {
		clock.addScoreBoardListener(new ConditionalScoreBoardListener(clock, Clock.EVENT_MAXIMUM_TIME, listener));
		
		clock.setMaximumTime(1000);
		advance(0);
		collectedEvents.clear();
		
		clock.changeMaximumTime(2000);
		advance(0);
		
		assertEquals(0, clock.getMinimumTime());
		assertEquals(3000, clock.getMaximumTime());
		assertEquals(0, clock.getTime());
		assertEquals(1, collectedEvents.size());
		ScoreBoardEvent event = collectedEvents.poll();
		assertEquals(3000, (long)event.getValue());
		assertEquals(1000, (long)event.getPreviousValue());
	}
	
	@Test
	public void testChangeMinimumTime() {
		clock.addScoreBoardListener(new ConditionalScoreBoardListener(clock, Clock.EVENT_MINIMUM_TIME, listener));
		
		clock.setMinimumTime(5000);
		advance(0);
		collectedEvents.clear();

		clock.changeMinimumTime(2000);
		advance(0);
		
		assertEquals(7000, clock.getMinimumTime());
		assertEquals(7000, clock.getMaximumTime());
		assertEquals(7000, clock.getTime());

		assertEquals(1, collectedEvents.size());
		ScoreBoardEvent event = collectedEvents.poll();
		assertEquals(7000, (long)event.getValue());
		assertEquals(5000, (long)event.getPreviousValue());
	}
	
	@Test
	public void testChangeTime() {
		clock.addScoreBoardListener(new ConditionalScoreBoardListener(clock, Clock.EVENT_TIME, listener));
		
		clock.setMaximumTime(5000);
		clock.setMinimumTime(1000);
		advance(0);
		collectedEvents.clear();
		
		clock.setTime(2000);
		advance(0);
		assertEquals(2000, clock.getTime());
		assertEquals(3000, clock.getInvertedTime());
		assertEquals(1, collectedEvents.size());
		ScoreBoardEvent event = collectedEvents.poll();
		assertEquals(2000, (long)event.getValue());
		assertEquals(1000, (long)event.getPreviousValue());
		
		clock.setTime(6000);
		advance(0);
		assertEquals(5000, clock.getTime());
		assertEquals(0, clock.getInvertedTime());
		assertEquals(1, collectedEvents.size());
		assertEquals(5000, (long)collectedEvents.poll().getValue());
		
		clock.setTime(400);
		advance(0);
		assertEquals(1000, clock.getTime());
		assertEquals(4000, clock.getInvertedTime());
		assertEquals(1, collectedEvents.size());
		collectedEvents.clear();
		
		clock.setTime(1200);
		advance(0);
		assertEquals(1200, clock.getTime());
		assertEquals(3800, clock.getInvertedTime());
		assertEquals(0, collectedEvents.size());
		
		clock.changeTime(-201);
		advance(0);
		assertEquals(999, clock.getTime());
		assertEquals(1, collectedEvents.size());
		collectedEvents.clear();

		clock.setCountDirectionDown(true);
		clock.changeTime(1);
		advance(0);
		assertEquals(0, collectedEvents.size());

		clock.setTime(2000);
		clock.changeTime(1200);
		advance(0);
		assertEquals(3200, clock.getTime());
		assertEquals(1800, clock.getInvertedTime());
		assertEquals(2, collectedEvents.size());
		collectedEvents.clear();
		
		clock.addScoreBoardListener(new ConditionalScoreBoardListener(clock, Clock.EVENT_INVERTED_TIME, listener));
		clock.changeTime(-5000);
		advance(0);
		assertEquals(1000, clock.getTime());
		assertEquals(4000, clock.getInvertedTime());
		assertEquals(2, collectedEvents.size());
		Boolean firstEventInverted;
		event = collectedEvents.poll();
		if (event.getProperty() == Clock.EVENT_TIME) {
			firstEventInverted = false;
			assertEquals(1000, (long)event.getValue());
			assertEquals(3200, (long)event.getPreviousValue());
		} else {
			firstEventInverted = true;
			assertEquals(4000, (long)event.getValue());
			assertEquals(1800, (long)event.getPreviousValue());			
		}
		event = collectedEvents.poll();
		if (firstEventInverted) {
			assertEquals(1000, (long)event.getValue());
			assertEquals(3200, (long)event.getPreviousValue());
		} else {
			assertEquals(4000, (long)event.getValue());
			assertEquals(1800, (long)event.getPreviousValue());			
		}
		
		clock.changeTime(4100);
		advance(0);
		assertEquals(5100, clock.getTime());
		assertEquals(-100, clock.getInvertedTime());
	}
	
	@Test
	public void testElapseTime_countUp()
	{
		clock.addScoreBoardListener(new ConditionalScoreBoardListener(clock, Clock.EVENT_TIME, listener));
		clock.setMaximumTime(5000);
		
		clock.setTime(2000);
		assertEquals(2000, clock.getTimeElapsed());
		assertEquals(3000, clock.getTimeRemaining());

		clock.elapseTime(1000);
		advance(0);
		assertEquals(3000, clock.getTime());
		assertEquals(3000, clock.getTimeElapsed());
		assertEquals(2000, clock.getTimeRemaining());
		assertEquals(2, collectedEvents.size());
	}

	@Test
	public void testElapseTime_countDown()
	{
		clock.setCountDirectionDown(true);
		clock.setMaximumTime(5000);
		
		clock.setTime(2000);
		assertEquals(3000, clock.getTimeElapsed());
		assertEquals(2000, clock.getTimeRemaining());

		clock.elapseTime(1000);
		assertEquals(1000, clock.getTime());
		assertEquals(4000, clock.getTimeElapsed());
		assertEquals(1000, clock.getTimeRemaining());
	}
	
	@Test
	public void testIsTimeAtStart_countUp()
	{
		clock.setMaximumTime(5000);
		
		assertTrue(clock.isTimeAtStart());
		assertFalse(clock.isTimeAtEnd());
		
		clock.setTime(2000);
		
		assertFalse(clock.isTimeAtStart());
		assertFalse(clock.isTimeAtEnd());
		
		clock.setTime(5000);
		
		assertFalse(clock.isTimeAtStart());
		assertTrue(clock.isTimeAtEnd());
	}
	
	@Test
	public void testIsTimeAtStart_countDown()
	{
		clock.setCountDirectionDown(true);
		clock.setMaximumTime(5000);
		clock.setTime(5000);
		
		assertTrue(clock.isTimeAtStart());
		assertFalse(clock.isTimeAtEnd());
		
		clock.setTime(2000);
		
		assertFalse(clock.isTimeAtStart());
		assertFalse(clock.isTimeAtEnd());
		
		clock.setTime(0);
		
		assertFalse(clock.isTimeAtStart());
		assertTrue(clock.isTimeAtEnd());
	}
	
	@Test
	public void testResetTime()
	{
		clock.addScoreBoardListener(new ConditionalScoreBoardListener(clock, Clock.EVENT_TIME, listener));
		clock.setMaximumTime(5000);
		clock.setMinimumTime(1000);
		
		clock.setTime(3000);
		
		clock.resetTime();
		advance(0);
		
		assertEquals(1000, clock.getTime());
		assertEquals(3, collectedEvents.size());
		
		clock.setTime(3000);
		clock.setCountDirectionDown(true);
		
		clock.resetTime();
		
		assertEquals(5000, clock.getTime());
	}
	
	public void testRunning() {
		clock.addScoreBoardListener(new ConditionalScoreBoardListener(clock, Clock.EVENT_RUNNING, listener));
		clock.addScoreBoardListener(new ConditionalScoreBoardListener(clock, Clock.EVENT_TIME, listener));
		clock.setMaximumTime(30000);
		assertFalse(clock.isCountDirectionDown());
		assertTrue(clock.isTimeAtStart());

		clock.start();
		advance(0);
		assertEquals(1, collectedEvents.size());
		ScoreBoardEvent event = collectedEvents.poll();
		assertEquals(Clock.EVENT_RUNNING, event.getProperty());
		assertTrue((Boolean)event.getValue());
		assertFalse((Boolean)event.getPreviousValue());
		
		advance(1000);
		advance(500);
		advance(500);
		assertEquals(2000, clock.getTimeElapsed());
		assertEquals(2, collectedEvents.size());
		assertEquals(Clock.EVENT_TIME, collectedEvents.poll().getProperty());
		assertEquals(Clock.EVENT_TIME, collectedEvents.poll().getProperty());
		
		advance(2000);
		assertEquals(4000, clock.getTimeElapsed());
		assertEquals(1, collectedEvents.size());
		assertEquals(Clock.EVENT_TIME, collectedEvents.poll().getProperty());

		clock.stop();
		advance(0);
		assertEquals(1, collectedEvents.size());
		event = collectedEvents.poll();
		assertEquals(Clock.EVENT_RUNNING, event.getProperty());
		assertFalse((Boolean)event.getValue());
		assertTrue((Boolean)event.getPreviousValue());

		clock.setCountDirectionDown(true);
		clock.resetTime();
		clock.start();
		advance(2000);
		assertEquals(2000, clock.getTimeElapsed());
	}

	@Test
	public void testRunningDownHasZeroTimeEvent() {
		clock.setCountDirectionDown(true);
		clock.setMaximumTime(1000);
		clock.setTime(1000);

		clock.addScoreBoardListener(new ConditionalScoreBoardListener(clock, Clock.EVENT_TIME, listener));
		clock.start();
		advance(200);
		advance(200);
		advance(200);
		advance(200);
		advance(200);
		assertEquals(1, collectedEvents.size());
		ScoreBoardEvent event = collectedEvents.poll();
		assertEquals(0, ((Long)event.getValue()).longValue());
	}
	
	@Test
	public void testStartNext() {
		clock.setMaximumNumber(5);
		clock.setNumber(2);
		clock.setMaximumTime(60000);
		clock.setTime(45000);
		assertFalse(clock.isRunning());
		advance(0);
		
		clock.startNext();
		advance(0);
		assertTrue(clock.isRunning());
		assertEquals(3, clock.getNumber());
		assertTrue(clock.isTimeAtStart());
	}
	
	@Test
	public void testApplyRule_name()
	{
		clock.addScoreBoardListener(new ConditionalScoreBoardListener(clock, Clock.EVENT_NAME, listener));
		clock.applyRule("Clock." + ID + ".Name", "New Name");
		advance(0);
		assertEquals("New Name", clock.getName());
		assertEquals(1, collectedEvents.size());
		
		clock.applyRule("Clock.OTHER.Name", "Shouldn't Change");
		assertEquals("New Name", clock.getName());
	}
	
	@Test
	public void testApplyRule_direction()
	{
		clock.addScoreBoardListener(new ConditionalScoreBoardListener(clock, Clock.EVENT_DIRECTION, listener));
		clock.applyRule("Clock." + ID + ".Direction", true);
		advance(0);
		assertTrue(clock.isCountDirectionDown());
		assertEquals(1, collectedEvents.size());
		
		clock.applyRule("Clock.OTHER.Direction", false);
		assertTrue(clock.isCountDirectionDown());
	}
	
	@Test
	public void testApplyRule_minimumNumber()
	{
		clock.addScoreBoardListener(new ConditionalScoreBoardListener(clock, Clock.EVENT_MINIMUM_NUMBER, listener));
		clock.applyRule("Clock." + ID + ".MinimumNumber", 10);
		advance(0);
		assertEquals(10, clock.getMinimumNumber());
		assertEquals(10, clock.getMaximumNumber());
		assertEquals(10, clock.getNumber());
		assertEquals(1, collectedEvents.size());

		
		clock.applyRule("Clock.OTHER.MaximumNumber", 20);
		assertEquals(10, clock.getMinimumNumber());
		assertEquals(10, clock.getMaximumNumber());
		assertEquals(10, clock.getNumber());
	}
	
	@Test
	public void testApplyRule_maximumNumber()
	{
		clock.addScoreBoardListener(new ConditionalScoreBoardListener(clock, Clock.EVENT_MAXIMUM_NUMBER, listener));
		clock.applyRule("Clock." + ID + ".MaximumNumber", 10);
		advance(0);
		assertEquals(0, clock.getMinimumNumber());
		assertEquals(10, clock.getMaximumNumber());
		assertEquals(0, clock.getNumber());
		assertEquals(1, collectedEvents.size());

		
		clock.applyRule("Clock.OTHER.MaximumNumber", 20);
		assertEquals(0, clock.getMinimumNumber());
		assertEquals(10, clock.getMaximumNumber());
		assertEquals(0, clock.getNumber());
	}
	
	@Test
	public void testApplyRule_minimumTime()
	{
		clock.addScoreBoardListener(new ConditionalScoreBoardListener(clock, Clock.EVENT_MINIMUM_TIME, listener));
		clock.applyRule("Clock." + ID + ".MinimumTime", (long)10000);
		advance(0);
		assertEquals(10000, clock.getMinimumTime());
		assertEquals(10000, clock.getMaximumTime());
		assertEquals(10000, clock.getTime());
		assertEquals(1, collectedEvents.size());

		
		clock.applyRule("Clock.OTHER.MinimumTime", (long)20000);
		assertEquals(10000, clock.getMinimumTime());
		assertEquals(10000, clock.getMaximumTime());
		assertEquals(10000, clock.getTime());
	}
	
	@Test
	public void testApplyRule_maximumTime()
	{
		clock.addScoreBoardListener(new ConditionalScoreBoardListener(clock, Clock.EVENT_MAXIMUM_TIME, listener));
		clock.applyRule("Clock." + ID + ".MaximumTime", (long)10000);
		advance(0);
		assertEquals(0, clock.getMinimumTime());
		assertEquals(10000, clock.getMaximumTime());
		assertEquals(0, clock.getTime());
		assertEquals(1, collectedEvents.size());

		
		clock.applyRule("Clock.OTHER.MaximumTime", (long)20000);
		assertEquals(0, clock.getMinimumTime());
		assertEquals(10000, clock.getMaximumTime());
		assertEquals(0, clock.getTime());
	}
}
