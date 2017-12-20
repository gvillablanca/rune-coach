package com.derpthemeus.runeCoach.databasePopulator.populatorThreads;

import com.derpthemeus.runeCoach.databasePopulator.PopulatorThread;
import com.derpthemeus.runeCoach.databasePopulator.threadManagers.SummonerLeagueUpdaterSupervisor;
import com.derpthemeus.runeCoach.hibernate.LeagueEntity;
import com.derpthemeus.runeCoach.hibernate.SummonerEntity;
import no.stelar7.api.l4j8.basic.constants.api.Platform;
import no.stelar7.api.l4j8.basic.constants.types.GameQueueType;
import no.stelar7.api.l4j8.pojo.league.LeaguePosition;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.List;

/**
 * Updates the `league` field for summoners in the database that have not been updated in a long time, or have not been set yet.
 */
public class SummonerLeagueUpdaterThread extends PopulatorThread {

	private SummonerEntity summoner;

	@Override
	public void runOperation() {
		Transaction tx = null;
		try (Session session = getSupervisor().getSessionFactory().openSession()) {
			tx = session.beginTransaction();
			summoner = getSupervisor().getSummonerToUpdate();

			List<LeaguePosition> leaguePositions = getSupervisor().getL4j8().getLeagueAPI().getLeaguePosition(Platform.NA1, summoner.getSummonerId());

			for (LeaguePosition position : leaguePositions) {
				if (position.getQueueType() == GameQueueType.RANKED_SOLO_5X5) {
					// Track the league, if it isn't already tracked
					Query leagueQuery = session.createQuery("FROM LeagueEntity WHERE uuid = :leagueId").setParameter("leagueId", position.getLeagueId());
					if (leagueQuery.uniqueResult() == null) {
						LeagueEntity leagueEntity = new LeagueEntity();
						leagueEntity.setUuid(position.getLeagueId());
						leagueEntity.setLastChecked(new Timestamp(0));
						session.save(leagueEntity);
					}

					summoner.setLeague(position.getLeagueId());
					summoner.setLeagueLastUpdated(new Timestamp(Calendar.getInstance().getTimeInMillis()));
					session.update(summoner);

					break;
				}
			}

			tx.commit();
		} catch (HibernateException ex) {
			if (tx != null) {
				tx.markRollbackOnly();
			}
			handleException(ex);
		}
		summoner = null;
	}

	public SummonerEntity getActiveSummoner() {
		return summoner;
	}

	@Override
	public SummonerLeagueUpdaterSupervisor getSupervisor() {
		return SummonerLeagueUpdaterSupervisor.getInstance();
	}
}